package top.yzljc.atribot.chat.official.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;

/**
* @Author AndyOctopus
* @ClassName AiModerationService
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Slf4j
public final class AiModerationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String FORMAT_SUFFIX = """

            请严格按以下 JSON 格式输出，不要输出任何 JSON 以外的内容：
            {"violation": true 或 false, "reason": "简要说明"}""";

    public static AiModerationVerdict reviewMessage(String systemPrompt, String content) {
        String prompt = (systemPrompt == null ? "" : systemPrompt) + FORMAT_SUFFIX;
        String response = Atri.getInstance().getAiService().askWithSystemPrompt(content == null ? "" : content, prompt);
        return parse(response);
    }

    public static AiModerationVerdict reviewJoinRequest(String systemPrompt, String question, String answer) {
        String prompt = (systemPrompt == null ? "" : systemPrompt) + FORMAT_SUFFIX;
        String userMessage = (question == null || question.isBlank() ? "" : "问题: " + question + "\n")
                + "回答: " + (answer == null ? "" : answer);
        String response = Atri.getInstance().getAiService().askWithSystemPrompt(userMessage, prompt);
        return parse(response);
    }

    private static AiModerationVerdict parse(String response) {
        if (response == null || response.isBlank()) {
            return new AiModerationVerdict(false, "AI 无响应");
        }
        try {
            JsonNode node = MAPPER.readTree(extractJson(response));
            return new AiModerationVerdict(node.path("violation").asBoolean(false), node.path("reason").asText(""));
        } catch (Exception e) {
            log.error("解析 AI 审核结果失败: {}", response, e);
            return new AiModerationVerdict(false, "AI 输出解析失败");
        }
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            return text;
        }
        return text.substring(start, end + 1);
    }
}
