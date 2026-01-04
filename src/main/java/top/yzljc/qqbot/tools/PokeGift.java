package top.yzljc.qqbot.tools;

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
import java.util.Set;

/**
 * 戳一戳回礼工具
 * 监听戳一戳事件，如果是戳机器人，则回击1次
 */
public class PokeGift {

    private static final String POKE_API = "http://106.14.23.232:8848/group_poke";
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    static Settings settings = Config.getInstance();
    private static final long botQq = settings.getBotUid();

    public static void process(JsonNode json) {
        // 1. 判断是否为通知类型 (Notice)
        String postType = json.path("post_type").asText("");
        if (!"notice".equals(postType)) {
            return;
        }

        // 2. 判断是否为戳一戳 (notify + poke)
        // NapCat/OneBot11 标准通常是: notice_type=notify, sub_type=poke
        String noticeType = json.path("notice_type").asText("");
        String subType = json.path("sub_type").asText("");

        if ("notify".equals(noticeType) && "poke".equals(subType)) {
            long targetId = json.path("target_id").asLong();

            // 3. 判断被戳的对象是不是我们自己 (BOT_QQ)
            if (targetId == botQq) {
                long groupId = json.path("group_id").asLong();
                long userId = json.path("user_id").asLong(); // 戳我的人

                // 简单的防死循环：如果是机器人自己戳自己（虽然不太可能），忽略
                if (userId == botQq) return;

                System.out.println("[INFO] 监测到用户 " + userId + " 在群 " + groupId + " 戳了机器人，准备反击！");
                
                sendPoke(groupId, userId);
            }
        }
    }

    private static void sendPoke(long groupId, long userId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("group_id", groupId);
            data.put("user_id", userId);

            String payload = jsonMapper.writeValueAsString(data);

            HttpURLConnection conn = (HttpURLConnection) new URL(POKE_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            // 设置较短的超时，追求速度
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            // 读取响应码以确保请求发出
            int code = conn.getResponseCode();
            if (conn.getInputStream() != null) {
                conn.getInputStream().close();
            }

            // 可以在调试时打开，平时关掉以免刷屏
            // System.out.println("[INFO] 反击发送状态: " + code);

        } catch (Exception e) {
            System.err.println("[INFO] 戳一戳发送失败: " + e.getMessage());
        }
    }
}