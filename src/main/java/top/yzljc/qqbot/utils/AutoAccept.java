package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
public class AutoAccept {

    private static final Logger log = LoggerFactory.getLogger(AutoAccept.class);
    
    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String API_URL = BASEURL + "/set_friend_add_request";
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
            log.warn("[WARN] 收到好友请求但 flag 为空，无法处理！");
            return;
        }

        log.info("收到好友请求 -> 用户: {} | 验证消息: {} | Flag: {}", userId, comment, flag);

        // 异步执行同意操作
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                approveFriendRequest(flag);
            } catch (Exception e) {
                log.warn("[INFO] 同意操作失败: {}", e.getMessage(), e);
            }
        });
    }

    private static void approveFriendRequest(String flag) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("approve", true);
        params.put("remark", ""); // 备注留空

        String payload = jsonMapper.writeValueAsString(params);

        HttpURLConnection conn = (HttpURLConnection) new URI(API_URL).toURL().openConnection();
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
            log.info("已成功同意好友请求 (Flag: {})", flag);
        } else {
            log.warn("API 请求返回错误代码: {}", code);
        }
    }
}
