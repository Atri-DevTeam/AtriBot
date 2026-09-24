package top.yzljc.atribot.service.textreview;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atribot.service.ai.AiProperties;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @Author YZ_Ljc_
 * @ClassName AiTextReviewClient
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.textreview
 * @Description 使用 default 模型定位违规片段，由本地代码执行替换
 */
final class AiTextReviewClient implements Function<String, List<TextRange>> {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final AiProperties properties;
    private final Duration timeout;
    private final String prompt;

    AiTextReviewClient(AiProperties properties, Duration timeout, String prompt) {
        this.properties = properties;
        this.timeout = timeout;
        this.prompt = prompt;
    }

    @Override
    public List<TextRange> apply(String text) {
        if (properties == null || blank(properties.getBaseUrl()) || blank(properties.getModel())) {
            throw new TextReviewException("default 模型地址或名称未配置");
        }
        try {
            Map<String, Object> body = new HashMap<>();
            if (properties.getExtraBody() != null) body.putAll(properties.getExtraBody());
            body.put("model", properties.getModel());
            body.put("stream", false);
            body.put("n", 1);
            body.put("messages", List.of(Map.of("role", "system", "content", prompt),
                    Map.of("role", "user", "content", text)));
            HttpRequest.Builder builder = HttpService.newRequestBuilder()
                    .uri(URI.create(properties.getBaseUrl())).timeout(timeout)
                    .header("Content-Type", "application/json");
            if (!blank(properties.getApiKey())) builder.header("Authorization", "Bearer " + properties.getApiKey());
            HttpResponse<String> response = HttpService.httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TextReviewException("AI 审核 HTTP 状态异常: " + response.statusCode());
            }
            JsonNode root = MAPPER.readTree(response.body());
            if (root == null) throw new TextReviewException("AI 审核响应为空");
            JsonNode choice = root.path("choices").path(0);
            if (!"stop".equals(choice.path("finish_reason").asText())) {
                throw new TextReviewException("AI 审核未完整结束");
            }
            JsonNode content = choice.path("message").path("content");
            if (!content.isTextual()) throw new TextReviewException("AI 审核缺少文本结果");
            return parse(text, content.textValue());
        } catch (TextReviewException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new TextReviewException("AI 审核请求失败: " + e.getClass().getSimpleName(), e);
        }
    }

    static List<TextRange> parse(String source, String content) {
        try {
            JsonNode result = MAPPER.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(content);
            if (result == null || !result.isObject() || result.size() != 1 || !result.path("violations").isArray()) {
                throw new TextReviewException("AI 审核结果缺少 violations 数组");
            }
            List<TextRange> ranges = new ArrayList<>();
            for (JsonNode violation : result.path("violations")) {
                if (!violation.isObject() || !violation.path("text").isTextual()
                        || !violation.path("occurrence").isIntegralNumber()
                        || !violation.path("occurrence").canConvertToInt()) {
                    throw new TextReviewException("AI 审核片段格式无效");
                }
                String fragment = violation.path("text").textValue();
                int occurrence = violation.path("occurrence").intValue();
                if (fragment.isBlank() || occurrence <= 0 || occurrence > source.length()) {
                    throw new TextReviewException("AI 审核片段或出现次数无效");
                }
                int start = -1;
                int from = 0;
                for (int count = 0; count < occurrence; count++) {
                    start = source.indexOf(fragment, from);
                    if (start < 0) throw new TextReviewException("AI 返回了无法在原文定位的片段");
                    from = start + fragment.length();
                }
                int end = start + fragment.length();
                if ((start > 0 && Character.isLowSurrogate(source.charAt(start))
                        && Character.isHighSurrogate(source.charAt(start - 1)))
                        || (end < source.length() && Character.isLowSurrogate(source.charAt(end))
                        && Character.isHighSurrogate(source.charAt(end - 1)))) {
                    throw new TextReviewException("AI 返回的片段截断了 Unicode 字符");
                }
                ranges.add(new TextRange(start, end));
            }
            return List.copyOf(ranges);
        } catch (TextReviewException e) {
            throw e;
        } catch (Exception e) {
            throw new TextReviewException("AI 审核结果无法解析", e);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value);
    }
}
