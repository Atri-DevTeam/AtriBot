package top.yzljc.qqbot.botkits.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String NAPCAT_API = BASEURL + "/send_group_msg";
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * 发送纯文本群消息
     */
    public static void sendGroupMessage(long groupId, String content) {
        sendGroupMessage(groupId, content, null);
    }

    /**
     * 发送带图片的群消息 (重载方法)
     * @param groupId 群号
     * @param text 文本内容
     * @param base64Image 图片的Base64字符串 (如果不发图片则传 null)
     */
    public static void sendGroupMessage(long groupId, String text, String base64Image) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<Map<String, Object>> messageNodes = new ArrayList<>();

                if (text != null && !text.isEmpty()) {
                    Map<String, Object> textData = new HashMap<>();
                    textData.put("text", text);
                    Map<String, Object> textNode = new HashMap<>();
                    textNode.put("type", "text");
                    textNode.put("data", textData);
                    messageNodes.add(textNode);
                }

                if (base64Image != null && !base64Image.isEmpty()) {
                    Map<String, Object> imgData = new HashMap<>();
                    imgData.put("file", "base64://" + base64Image);

                    Map<String, Object> imgNode = new HashMap<>();
                    imgNode.put("type", "image");
                    imgNode.put("data", imgData);

                    messageNodes.add(imgNode);
                }

                if (messageNodes.isEmpty()) return;

                Map<String, Object> payloadMap = new HashMap<>();
                payloadMap.put("group_id", groupId);
                payloadMap.put("message", messageNodes);

                String payload = jsonMapper.writeValueAsString(payloadMap);

                HttpURLConnection conn = (HttpURLConnection) new URI(NAPCAT_API).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (conn.getInputStream() != null) conn.getInputStream().close();

                if (code == 200) {
                    log.info("消息发送成功{} -> 群: {}", (base64Image != null ? " [含图片]" : ""), groupId);
                } else {
                    log.error("消息发送失败，HTTP Code: {}", code);
                }
            } catch (Exception ex) {
                log.error("推送异常：{}", ex.getMessage());
            }
        });
    }

    public static Long sendGroupMessageGetId(long groupId, String content) {
        try {
            List<Map<String, Object>> messageNodes = new ArrayList<>();

            if (content != null && !content.isEmpty()) {
                Map<String, Object> textData = new HashMap<>();
                textData.put("text", content);
                Map<String, Object> textNode = new HashMap<>();
                textNode.put("type", "text");
                textNode.put("data", textData);
                messageNodes.add(textNode);
            }

            if (messageNodes.isEmpty()) return null;

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", messageNodes);

            String payload = jsonMapper.writeValueAsString(payloadMap);

            HttpURLConnection conn = (HttpURLConnection) new URI(NAPCAT_API).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();

            if (code == 200) {
                try (InputStream is = conn.getInputStream()) {
                    // 读取整个响应体
                    StringBuilder sb = new StringBuilder();
                    byte[] buf = new byte[512];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        sb.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                    }
                    // 解析 message_id
                    com.fasterxml.jackson.databind.JsonNode resp = jsonMapper.readTree(sb.toString());
                    if (resp.has("data") && resp.get("data").has("message_id")) {
                        return resp.get("data").get("message_id").asLong();
                    }
                }
            } else {
                log.error("消息发送失败，HTTP Code: {}", code);
            }
        } catch (Exception ex) {
            log.error("同步消息推送异常：{}", ex.getMessage());
        }
        return null;
    }
}
