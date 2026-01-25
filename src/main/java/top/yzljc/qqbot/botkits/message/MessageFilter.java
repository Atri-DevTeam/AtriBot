package top.yzljc.qqbot.botkits.message;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * 群消息实时过滤器
 * 检测到违规词立即撤回
 */
public class MessageFilter {

    private static final Logger log = LoggerFactory.getLogger(MessageFilter.class);

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
            log.info("检测到违规词：{}，已尝试撤回。来自 QQ: {}, 消息 ID: {}", detectedWord, userId, messageId);
            
	    }
    }

    private static void recallMessageSilent(long messageId) {
        try {
            URL url = new URI(DELETE_API).toURL();
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
            log.error("撤回消息（ID: {}）失败: {}", messageId, e.getMessage());
        }
    }
}
