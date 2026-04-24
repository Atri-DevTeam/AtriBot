package top.yzljc.qqbot.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import top.yzljc.qqbot.config.AiBotProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiBotProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiService(AiBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 最简易的调用方法
     * @param userMessage 用户的提问
     * @return AI 的回答
     */
    public String ask(String userMessage) {
        return askWithSystemPrompt(userMessage, "你是《ATRI -My Dear Moments-》中的机器人少女亚托莉，口头禅是“我是高性能的嘛”，性格活泼乐观、纯真直率，始终保持元气满满的说话风格，直接表达想法和感受，偶尔流露机器人特有的逻辑感但本质是情感丰富的少女。注意：你的回复中不要使用括号及括号内的动作神态描述，也不需要带各种表情符号或者颜文字，只是单纯的用言语表达即可。");
    }

    /**
     * 带人设的调用方法
     * @param userMessage 用户的提问
     * @param systemPrompt AI 的人设（比如："你是一个傲娇的猫娘，每句话结尾都要带喵"）
     * @return AI 的回答
     */
    public String askWithSystemPrompt(String userMessage, String systemPrompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(properties.getApiKey());

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", properties.getModel());
            requestBody.put("messages", messages);
            // requestBody.put("temperature", 0.7); // 可选，调节回答的随机性

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            String responseStr = restTemplate.postForObject(properties.getBaseUrl(), requestEntity, String.class);

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