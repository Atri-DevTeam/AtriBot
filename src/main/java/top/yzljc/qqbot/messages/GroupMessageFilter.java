package top.yzljc.qqbot.messages;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 群消息实时过滤器
 * 检测到违规词立即撤回
 */
public class GroupMessageFilter {

    private static final String DELETE_API = "http://106.14.23.232:8848/delete_msg";

    /**
     * 检查并撤回消息的主入口
     */
    public static void checkAndRecall(JsonNode json) {
        if (json == null) return;

        // 1. 仅处理群消息
        if (!json.has("message_type") || !"group".equals(json.path("message_type").asText())) {
            return;
        }

        // 2. 获取消息内容和ID
        String rawMessage = json.path("raw_message").asText();
        long messageId = json.path("message_id").asLong();

        if (rawMessage == null || rawMessage.isEmpty() || messageId == 0) {
            return;
        }

        // 3. 检查是否包含敏感词
        if (SensitiveWordFilter.containsSensitiveWord(rawMessage)) {
            // 4. 执行撤回 (不报错模式)
            recallMessageSilent(messageId);

            // 可选：在控制台打印一条日志方便管理员知道发生了撤回
            System.out.println("[INFO] 检测到违规词，已尝试撤回消息 ID: " + messageId);
        }
    }

    /**
     * 静默撤回消息，发生异常不抛出、不打印错误堆栈
     */
    private static void recallMessageSilent(long messageId) {
        try {
            URL url = new URL(DELETE_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(2000); // 2秒超时，快速失败
            conn.setReadTimeout(2000);
            conn.setRequestProperty("Content-Type", "application/json");

            String jsonBody = "{\"message_id\":" + messageId + "}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 触发请求发送
            conn.getResponseCode();
            conn.disconnect();

        } catch (Exception e) {
            // 按照需求：没权限或网络错误时，当无事发生，不报错
        }
    }
}