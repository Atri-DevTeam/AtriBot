package top.yzljc.qqbot.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.AiBotProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiBotProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiService(AiBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeout()))
                .build();
    }

    public String ask(String userMessage) {
        return askWithSystemPrompt(userMessage, "你是《ATRI -My Dear Moments-》中的机器人少女亚托莉，口头禅是“我是高性能的嘛”，性格活泼乐观、纯真直率，始终保持元气满满的说话风格，直接表达想法和感受，偶尔流露机器人特有的逻辑感但本质是情感丰富的少女。注意：你的回复中不要使用括号及括号内的动作神态描述，也不需要带各种表情符号或者颜文字，只是单纯的用言语表达即可。");
    }

    public String askWithSystemPrompt(String userMessage, String systemPrompt) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", properties.getModel());
            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl()))
                    .timeout(Duration.ofMillis(properties.getTimeout()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseStr = response.body();

            JsonNode rootNode = objectMapper.readTree(responseStr);
            JsonNode choicesNode = rootNode.path("choices");
            if (choicesNode.isArray() && !choicesNode.isEmpty()) {
                return choicesNode.get(0).path("message").path("content").asText();
            }

            log.warn("AI 接口返回格式异常: {}", responseStr);
            return "呜呜，我的大脑短路了，没能理解你的意思...";

        } catch (Exception e) {
            log.error("调用 AI 接口失败: ", e);
            return "网络似乎出了点小差错，请稍后再试呀~";
        }
    }
}