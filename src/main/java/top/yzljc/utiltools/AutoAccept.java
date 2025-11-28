package top.yzljc.utiltools;

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
    // NapCat 自动同意好友请求的接口
    private static final String API_URL = "http://106.14.23.232:8848/set_friend_add_request";
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static void handle(JsonNode json) {
        // 1. 严格校验：必须是 post_type=request 且 request_type=friend
        if (!json.has("post_type") || !"request".equals(json.get("post_type").asText())) {
            return;
        }
        if (!json.has("request_type") || !"friend".equals(json.get("request_type").asText())) {
            return;
        }

        // 2. 提取字段 (根据你抓到的日志)
        // "flag": "1764309633"
        String flag = json.has("flag") ? json.get("flag").asText() : "";

        // "user_id": 3614865692
        long userId = json.has("user_id") ? json.get("user_id").asLong() : 0;

        // "comment": "我是来自..."
        String comment = json.has("comment") ? json.get("comment").asText() : "";

        // 如果没有 flag，没法同意，直接退出
        if (flag.isEmpty()) {
            System.err.println("[AutoAccept] 收到好友请求但 flag 为空，无法处理！");
            return;
        }

        System.out.printf("[AutoAccept] 收到好友请求 -> 用户: %d | 验证消息: %s | Flag: %s\n", userId, comment, flag);

        // 3. 异步发送同意指令
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                approveFriendRequest(flag);
            } catch (Exception e) {
                System.err.println("[AutoAccept] 同意操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void approveFriendRequest(String flag) throws Exception {
        // 构造 NapCat 需要的参数: {"flag": "...", "approve": true, "remark": ""}
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
        conn.getInputStream().close();

        if (code == 200) {
            System.out.println("[AutoAccept] 已成功同意好友请求 (Flag: " + flag + ")");
        } else {
            System.err.println("[AutoAccept] API 请求返回错误代码: " + code);
        }
    }
}