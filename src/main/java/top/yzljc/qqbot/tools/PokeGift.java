package top.yzljc.qqbot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 戳一戳回礼工具
 * 监听戳一戳事件，如果是戳机器人，则光速反击20次
 */
public class PokeGift {

    private static final String POKE_API = "http://106.14.23.232:8848/group_poke";
    private static final long BOT_QQ = 970717559L; // 机器人的QQ号
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // 使用 CachedThreadPool 以便在短时间内并发执行多个网络请求
    private static final ExecutorService WORKER_POOL = Executors.newCachedThreadPool();

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
            if (targetId == BOT_QQ) {
                long groupId = json.path("group_id").asLong();
                long userId = json.path("user_id").asLong(); // 戳我的人

                // 简单的防死循环：如果是机器人自己戳自己（虽然不太可能），忽略
                if (userId == BOT_QQ) return;

                System.out.println("[PokeGift] 监测到用户 " + userId + " 在群 " + groupId + " 戳了机器人，准备反击！");

                // 4. 光速连发 20 次
                for (int i = 0; i < 20; i++) {
                    // 放入线程池并发发送，不要阻塞主线程，也不要顺序发送（那样太慢）
                    WORKER_POOL.submit(() -> sendPoke(groupId, userId));
                }
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
            // System.out.println("[PokeGift] 反击发送状态: " + code);

        } catch (Exception e) {
            System.err.println("[PokeGift] 戳一戳发送失败: " + e.getMessage());
        }
    }
}