package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class AutoSign {
    private static final String GROUP_LIST_API = "http://106.14.23.232:8848/get_group_list";
    private static final String GROUP_SIGN_API = "http://106.14.23.232:8848/send_group_sign";
    private static final long ALLOWED_USER_ID = 3199590352L;
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 启动定时任务，每天0:00:10自动执行
    public static void startScheduler() {
        long initialDelay = computeInitialDelay();
        long oneDayMs = TimeUnit.DAYS.toMillis(1);
        scheduler.scheduleAtFixedRate(AutoSign::signAllGroups, initialDelay, oneDayMs, TimeUnit.MILLISECONDS);
        System.out.println("[INFO] 每天0:00:00自动群打卡任务已启动。首次延迟(ms): " + initialDelay);
    }

    // 计算距离下个0:00:10的毫秒数
    private static long computeInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(LocalTime.of(0, 0, 0));
        if (!now.isBefore(next)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    // 向全部群调用打卡API
    private static void signAllGroups() {
        try {
            Set<String> groupIds = fetchAllGroupIds();
            if (groupIds.isEmpty()) {
                System.out.println("[INFO] 未拉取到任何群号，自动打卡跳过。");
                return;
            }
            for (String groupId : groupIds) {
                sendGroupSign(groupId);
            }
            System.out.println("[INFO] 已对 " + groupIds.size() + " 个群执行自动打卡。");
        } catch (Exception e) {
            System.err.println("[INFO] 自动打卡异常: " + e.getMessage());
        }
    }

    /** 拉取所有群号，适配你的实际JSON格式(data为群列表，每个group_id为群号) */
    private static Set<String> fetchAllGroupIds() {
        Set<String> groupIds = new HashSet<>();
        String nextToken = "";
        try {
            while (true) {
                String reqJson = "{\"next_token\":\"" + nextToken + "\"}";
                HttpURLConnection conn = (HttpURLConnection) new URL(GROUP_LIST_API).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(reqJson.getBytes(StandardCharsets.UTF_8));
                }

                try (InputStream in = conn.getInputStream()) {
                    JsonNode resp = JSON_MAPPER.readTree(in);
                    // 适配实测json：data为array，每项有group_id
                    if (resp.has("data") && resp.get("data").isArray()) {
                        for (JsonNode group : resp.get("data")) {
                            if (group.has("group_id")) {
                                String gid = group.get("group_id").asText();
                                groupIds.add(gid);
                            }
                        }
                    }

                    // 判断有没有下一页（与napcat新接口兼容，通常实际只有一页）
                    if (resp.has("next_token")) {
                        String token = resp.get("next_token").asText();
                        if (token == null || token.isEmpty()) {
                            break;
                        } else {
                            nextToken = token;
                        }
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[INFO] 获取群列表异常: " + e.getMessage());
        }
        return groupIds;
    }

    /** 调用群打卡请求API */
    private static void sendGroupSign(String groupId) {
        try {
            String payload = "{\"group_id\":\"" + groupId + "\"}";
            HttpURLConnection conn = (HttpURLConnection) new URL(GROUP_SIGN_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            conn.getInputStream().close();
        } catch (Exception e) {
            System.err.println("[INFO] 群打卡失败 group_id=" + groupId + "  " + e.getMessage());
        }
    }

    /**
     * 仅允许 3199590352 发 testforsign（不区分群，所有群都能触发）立即执行一次打卡
     */
    public static void processAutoSign(JsonNode json) {
        long userId = json.path("user_id").asLong();
        String rawMessage = json.path("raw_message").asText().trim().toLowerCase();
        if (userId == ALLOWED_USER_ID && "testforsign".equals(rawMessage)) {
            Executors.newSingleThreadExecutor().submit(AutoSign::signAllGroups);
        }
    }
}