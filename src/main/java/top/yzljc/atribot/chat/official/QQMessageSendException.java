package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @Author YZ_Ljc_
 * @ClassName QQMessageSendException
 * @Created_at 2026/07/17
 * @Project AtriData
 * @Package top.yzljc.atribot.chat.official
 */
public class QQMessageSendException extends RuntimeException {

    private QQMessageSendException(String message) {
        super(message);
    }

    static QQMessageSendException fromResponse(ObjectMapper objectMapper, String responseBody, String fallbackMessage) {
        String message = extractMessage(objectMapper, responseBody);
        return new QQMessageSendException(message == null ? fallbackMessage : message);
    }

    private static String extractMessage(ObjectMapper objectMapper, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode message = response.get("message");
            if (message == null || message.isNull()) {
                return null;
            }
            String value = message.asText(null);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception ignored) {
            return null;
        }
    }
}
