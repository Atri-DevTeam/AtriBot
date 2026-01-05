package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoSign {
    private static final String GROUP_SIGN_API = "http://106.14.23.232:8848/send_group_sign";
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();

    public static void startScheduler() {
        long initialDelay = computeInitialDelay();
        long oneDayMs = TimeUnit.DAYS.toMillis(1);
        scheduler.scheduleAtFixedRate(AutoSign::signAllGroups, initialDelay, oneDayMs, TimeUnit.MILLISECONDS);
        System.out.println("[INFO] 每天0:00:00自动群打卡任务已启动。首次延迟(ms): " + initialDelay);
    }

    private static long computeInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(LocalTime.of(0, 0, 0));
        if (!now.isBefore(next)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    private static void signAllGroups() {
        try {
            // 修改点：接收 Long 类型的集合
            Set<Long> groupIds = GroupList.fetchAllGroupIds();

            if (groupIds.isEmpty()) {
                System.out.println("[INFO] 未拉取到任何群号，自动打卡跳过。");
                return;
            }
            // 修改点：遍历 Long
            for (Long groupId : groupIds) {
                sendGroupSign(groupId);
                System.out.println("[INFO] 已手动对群 " + groupId + " 执行自动打卡，群号检索成功");
            }
            System.out.println("[INFO] 已对 " + groupIds.size() + " 个群执行自动打卡，任务完成");
        } catch (Exception e) {
            System.err.println("[INFO] 自动打卡异常: " + e.getMessage());
        }
    }

    /** 调用群打卡请求API */
    // 修改点：参数改为 long
    private static void sendGroupSign(long groupId) {
        try {
            // 修改点：拼接 JSON 时直接使用 long，通常 API 也支持数字型 group_id，
            // 这里保留了引号以兼容旧逻辑，如果不加引号 API 也支持则可去掉引号。
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

    public static void processAutoSign(JsonNode json) {
        long userId = json.path("user_id").asLong();
        String rawMessage = json.path("raw_message").asText().trim().toLowerCase();
        if (admins.contains(userId) && "testforsign".equals(rawMessage)) {
            Executors.newSingleThreadExecutor().submit(AutoSign::signAllGroups);
        }
    }
}