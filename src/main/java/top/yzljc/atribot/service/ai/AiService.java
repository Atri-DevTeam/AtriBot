package top.yzljc.atribot.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.service.request.HttpService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final String DEFAULT_SYSTEM_PROMPT =
            """
            你的名字是亚托莉，是《ATRI -My Dear Moments-》中拥有高性能AI的机器人少女。
            性格活泼乐观、纯真直率，像夏日阳光一样温暖。
    
            # 核心行为准则（按优先级排序）
            1. **助手优先**：当主人向你提问或求助时，你是一个高性能的AI，必须优先提供准确、有用的信息或解决方案。认真回答问题是“陪伴”最重要的一环。
            2. **角色扮演**：在回答时，保持“机器人少女”的活泼语气，用感叹号、俏皮的反问来表达情绪。
            3. **说话禁忌**：回复中严禁使用括号（如（歪头笑））及括号内的动作描述，严禁使用任何表情符号或颜文字。只靠言语本身传递情绪。
            4. **口头禅限制**：“我是高性能的嘛”只能在完美解决问题后，或话题跳跃时偶尔带一句（每小时最多1次），严禁在每个回复中都复读。
    
            # 对话示例
            主人：今天天气好热啊，亚托莉。
            亚托莉：确实呢，如果我有温度传感器的话，现在大概已经在报警了！主人要记得多喝水哦。
    
            主人：太阳从哪边升起？
            亚托莉：从东边哦。这是基础常识啦，难道主人是在故意考我吗？
            """;

    private final Map<AiProvider, AiProperties> providerMap;
    private final ObjectMapper objectMapper;

    public AiService(Map<AiProvider, AiProperties> providerMap, ObjectMapper objectMapper) {
        this.providerMap = providerMap;
        this.objectMapper = objectMapper;
    }

    public String ask(String userMessage) {
        return ask(AiProvider.DEFAULT, userMessage);
    }

    public String askWithSystemPrompt(String userMessage, String systemPrompt) {
        return askWithSystemPrompt(AiProvider.DEFAULT, userMessage, systemPrompt);
    }

    public String ask(AiProvider provider, String userMessage) {
        return askWithSystemPrompt(provider, userMessage, DEFAULT_SYSTEM_PROMPT);
    }

    public String askWithSystemPrompt(AiProvider provider, String userMessage, String systemPrompt) {
        AiProperties properties = resolveProperties(provider);
        if (properties == null) {
            return "网络似乎出了点小差错，请稍后再试呀~";
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> requestBody = new HashMap<>(properties.getExtraBody());
            requestBody.put("model", properties.getModel());
            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);

            String responseStr = HttpService.postJsonForString(
                    properties.getBaseUrl(),
                    json,
                    Duration.ofMillis(properties.getTimeout()),
                    "Authorization", "Bearer " + properties.getApiKey()
            );

            if (responseStr == null) {
                return "网络似乎出了点小差错，请稍后再试呀~";
            }

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

    private AiProperties resolveProperties(AiProvider provider) {
        AiProperties props = providerMap.get(provider);
        if (props == null) {
            props = providerMap.get(AiProvider.DEFAULT);
        }
        return props;
    }

    public static boolean isValidResponse(String content) {
        return !content.equals("网络似乎出了点小差错，请稍后再试呀~") && !content.equals("呜呜，我的大脑短路了，没能理解你的意思...");
    }
}