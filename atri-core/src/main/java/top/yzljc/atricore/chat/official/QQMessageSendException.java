package top.yzljc.atricore.chat.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName QQMessageSendException
 * @Created_at 2026/07/17
 * @Project AtriData
 * @Package top.yzljc.atricore.chat.official
 */
@Getter
public class QQMessageSendException extends RuntimeException {
    private final Integer code;
    private final Integer errCode;
    private final String traceId;

    private QQMessageSendException(String message, Integer code, Integer errCode, String traceId) {
        super(message);
        this.code = code;
        this.errCode = errCode;
        this.traceId = traceId;
    }

    public static QQMessageSendException fromResponse(ObjectMapper objectMapper, String responseBody, String fallbackMessage) {
        String message = extractMessage(objectMapper, responseBody);
        Integer code = null;
        Integer errCode = null;
        String traceId = null;
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode errCodeNode = response.get("err_code");
            if (errCodeNode != null && errCodeNode.asText().matches("-?\\d+")) errCode = Integer.valueOf(errCodeNode.asText());
            JsonNode codeNode = response.hasNonNull("code") ? response.get("code") : response.get("err_code");
            if (codeNode != null && codeNode.asText().matches("-?\\d+")) code = Integer.valueOf(codeNode.asText());
            traceId = response.path("trace_id").asText(null);
        } catch (Exception ignored) {
        }
        return new QQMessageSendException(message == null ? fallbackMessage : message, code, errCode, traceId);
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
