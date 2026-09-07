package top.yzljc.atribot.chat.official.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.HttpService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client for the shared lexicon + AI content censor service. */
@Slf4j
public final class AiModerationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_CENSOR_TYPE = 2;

    public static AiModerationVerdict reviewMessage(String systemPrompt, String content) {
        return review(content, systemPrompt, "", List.of(), DEFAULT_CENSOR_TYPE);
    }

    public static AiModerationVerdict reviewMessage(String systemPrompt, String customOutput,
                                                    List<String> allowedDomains, String content) {
        return review(content, systemPrompt, customOutput, allowedDomains, DEFAULT_CENSOR_TYPE);
    }

    public static AiModerationVerdict reviewMessage(String systemPrompt, String customOutput,
                                                    List<String> allowedDomains, int type, String content) {
        return review(content, systemPrompt, customOutput, allowedDomains, type);
    }

    public static AiModerationVerdict reviewJoinRequest(String systemPrompt, String question, String answer) {
        return reviewJoinRequest(systemPrompt, "", List.of(), DEFAULT_CENSOR_TYPE, question, answer);
    }

    public static AiModerationVerdict reviewJoinRequest(String systemPrompt, String customOutput,
                                                        List<String> allowedDomains, String question, String answer) {
        return reviewJoinRequest(systemPrompt, customOutput, allowedDomains, DEFAULT_CENSOR_TYPE, question, answer);
    }

    public static AiModerationVerdict reviewJoinRequest(String systemPrompt, String customOutput,
                                                        List<String> allowedDomains, int type,
                                                        String question, String answer) {
        String userMessage = (question == null || question.isBlank() ? "" : "问题: " + question + "\n")
                + "回答: " + (answer == null ? "" : answer);
        return review(userMessage, systemPrompt, customOutput, allowedDomains, type);
    }

    private static AiModerationVerdict review(String text, String customPrompt,
                                              String customOutput, List<String> allowedDomains, int type) {
        Config config = Config.getInstance();
        String apiKey = config.getCensorApiKey();
        if (apiKey == null || apiKey.isBlank() || isPlaceholder(apiKey)) {
            return new AiModerationVerdict(false, "内容审核 API 未配置 API Key");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text == null ? "" : text);
        body.put("type", normalizeType(type));
        if (customPrompt != null && !customPrompt.isBlank()) {
            body.put("customPrompt", customPrompt);
        }
        if (customOutput != null && !customOutput.isBlank()) {
            body.put("customOutput", customOutput);
        }
        if (allowedDomains != null && !allowedDomains.isEmpty()) {
            body.put("allowedDomains", allowedDomains);
        }

        String configuredUrl = config.getCensorApiUrl();
        if (configuredUrl == null || configuredUrl.isBlank()) {
            configuredUrl = config.getApiUrl();
        }
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return new AiModerationVerdict(false, "内容审核 API 未配置地址");
        }
        String endpoint = configuredUrl;
        try {
            String request = MAPPER.writeValueAsString(body);
            HttpService.PostResult result = HttpService.postJsonDetailed(endpoint, request,
                    "Authorization", "Bearer API-Key: " + apiKey,
                    "Accept", "application/json");
            if (result.status() < 200 || result.status() >= 300 || result.body() == null || result.body().isBlank()) {
                return new AiModerationVerdict(false, "内容审核 API 暂不可用（HTTP " + result.status() + "）");
            }
            return parse(result.body());
        } catch (Exception e) {
            log.warn("调用内容审核 API 失败", e);
            return new AiModerationVerdict(false, "内容审核 API 暂不可用");
        }
    }

    private static int normalizeType(int type) {
        return type >= 0 && type <= 3 ? type : DEFAULT_CENSOR_TYPE;
    }

    private static AiModerationVerdict parse(String response) throws Exception {
        JsonNode root = MAPPER.readTree(response);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return new AiModerationVerdict(false, root.path("message").asText("内容审核 API 返回异常"));
        }
        int level = data.path("level").asInt(0);
        String reason = data.path("reason").asText("");
        JsonNode customData = data.path("customData");
        List<String> hitWords = new ArrayList<>();
        JsonNode hits = data.path("hitWords");
        if (hits.isArray()) {
            hits.forEach(node -> hitWords.add(node.asText()));
        }
        return new AiModerationVerdict(level >= 3 || level == 101, reason,
                extractCustomMessage(customData), level, hitWords,
                data.path("provider").isNull() ? null : data.path("provider").asText(null));
    }

    private static String extractCustomMessage(JsonNode customData) {
        if (customData == null || customData.isMissingNode() || customData.isNull()) {
            return "";
        }
        try {
            JsonNode node = customData;
            // The documented response uses a JSON string, while some deployments
            // return the decoded object directly. Handle both forms.
            if (node.isTextual()) {
                String raw = node.asText();
                if (raw.isBlank() || "{}".equals(raw.trim())) {
                    return "";
                }
                try {
                    node = MAPPER.readTree(raw);
                } catch (Exception ignored) {
                    return raw.trim();
                }
            }
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText().trim();
            }
            for (String key : List.of("reply", "message", "text", "response", "content")) {
                JsonNode value = node.path(key);
                if (value.isTextual() && !value.asText().isBlank()) {
                    return value.asText().trim();
                }
            }
            String nested = firstTextValue(node);
            if (!nested.isBlank()) {
                return nested;
            }
        } catch (Exception ignored) {
            // Keep the normal static reminder when customData is malformed.
        }
        return "";
    }

    private static String firstTextValue(JsonNode node) {
        if (node == null) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (node.isContainerNode()) {
            var iterator = node.elements();
            while (iterator.hasNext()) {
                String value = firstTextValue(iterator.next());
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.equals("null") || normalized.equals("xxx") || normalized.equals("change-me")
                || normalized.equals("atri_sk_xxxxxxxx");
    }
}
