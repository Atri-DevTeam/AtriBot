package top.yzljc.qqbot.messages;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 群消息实时过滤器
 * 检测到违规词立即撤回
 */
public class GroupMessageFilter {

    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String DELETE_API = BASEURL + "/delete_msg";

    public static void checkAndRecall(JsonNode json) {
        if (json == null) return;

        if (!json.has("message_type") || !"group".equals(json.path("message_type").asText())) {
            return;
        }

        String rawMessage = json.path("raw_message").asText();
        long messageId = json.path("message_id").asLong();
        long userId = json.path("user_id").asLong();

        if (rawMessage == null || rawMessage.isEmpty() || messageId == 0) {
            return;
        }

        if (SensitiveWordFilter.containsSensitiveWord(rawMessage)) {
            recallMessageSilent(messageId);

            String detectedWord = SensitiveWordFilter.findSensitiveWord(rawMessage);
            System.out.println("[INFO] 检测到违规词 [" + detectedWord + "]，来自QQ:" + userId + "，已尝试撤回消息 ID: " + messageId);
        }
    }

    private static void recallMessageSilent(long messageId) {
        try {
            URL url = new URL(DELETE_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestProperty("Content-Type", "application/json");

            String jsonBody = "{\"message_id\":" + messageId + "}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            conn.getResponseCode();
            conn.disconnect();

        } catch (Exception e) {
            System.err.println("[ERROR] 撤回消息 ID " + messageId + " 失败: " + e.getMessage());
        }
    }
}