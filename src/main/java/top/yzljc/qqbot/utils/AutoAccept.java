package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class AutoAccept {
    // 这里的 API 地址与发送消息不同，是处理请求的 API
    private static final String API_URL = "http://106.14.23.232:8848/set_friend_add_request";
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * 处理好友请求逻辑
     * 该方法由 MessageProcessor 在检测到 post_type 为 request 时调用
     */
    public static void handle(JsonNode json) {
        // 二次校验，防止错误调用
        if (!json.has("post_type") || !"request".equals(json.get("post_type").asText())) {
            return;
        }
        if (!json.has("request_type") || !"friend".equals(json.get("request_type").asText())) {
            return;
        }

        String flag = json.has("flag") ? json.get("flag").asText() : "";
        long userId = json.has("user_id") ? json.get("user_id").asLong() : 0;
        String comment = json.has("comment") ? json.get("comment").asText() : "";

        if (flag.isEmpty()) {
            System.err.println("[INFO] 收到好友请求但 flag 为空，无法处理！");
            return;
        }

        System.out.printf("[INFO] 收到好友请求 -> 用户: %d | 验证消息: %s | Flag: %s\n", userId, comment, flag);

        // 异步执行同意操作
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                approveFriendRequest(flag);
            } catch (Exception e) {
                System.err.println("[INFO] 同意操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void approveFriendRequest(String flag) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("approve", true);
        params.put("remark", ""); // 备注留空

        String payload = jsonMapper.writeValueAsString(params);

        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        // 无论成功失败，都需要关闭流
        if (conn.getInputStream() != null) {
            conn.getInputStream().close();
        }

        if (code == 200) {
            System.out.println("[INFO] 已成功同意好友请求 (Flag: " + flag + ")");
        } else {
            System.err.println("[INFO] API 请求返回错误代码: " + code);
        }
    }
}