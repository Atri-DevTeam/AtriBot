package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.GroupConfigManager;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
public class AutoSign {

    private static final Logger log = LoggerFactory.getLogger(AutoSign.class);
    
    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String GROUP_SIGN_API = BASEURL + "/send_group_sign";
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
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
            Set<Long> groupIds = GroupList.fetchAllGroupIds();

            if (groupIds.isEmpty()) {
                System.out.println("[INFO] 未拉取到任何群号，自动打卡跳过。");
                return;
            }

            for (Long groupId : groupIds) {

                if (!GroupConfigManager.isFeatureEnabled(groupId, "auto_sign")) {
                    continue;
                }

                sendGroupSign(groupId);
                System.out.println("[INFO] 群 " + groupId + " 打卡成功");
            }
        } catch (Exception e) {
            System.err.println("[INFO] 自动打卡异常: " + e.getMessage());
        }
    }

    private static void sendGroupSign(long groupId) {
        try {
            String payload = "{\"group_id\":\"" + groupId + "\"}";

            HttpURLConnection conn = (HttpURLConnection) new URI(GROUP_SIGN_API).toURL().openConnection();
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
        if (admins.contains(userId) && "/testforsign".equals(rawMessage)) {
            Executors.newSingleThreadExecutor().submit(AutoSign::signAllGroups);
        }
    }
}
