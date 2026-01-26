package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.CheckType;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.Settings;

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
import top.yzljc.qqbot.config.groups.GroupList;

public class AutoSign {

    private static final Logger log = LoggerFactory.getLogger(AutoSign.class);
    
    static Settings settings = Config.getInstance();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final List<Long> admins = settings.getAdminUids();

    public static void startScheduler() {
        long initialDelay = computeInitialDelay();
        long oneDayMs = TimeUnit.DAYS.toMillis(1);
        scheduler.scheduleAtFixedRate(AutoSign::signAllGroups, initialDelay, oneDayMs, TimeUnit.MILLISECONDS);
        log.info("每天0:00:00自动群打卡任务已启动。首次延迟(ms):{}", initialDelay);
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
                log.warn("未获取到任何群号，自动打卡跳过");
                return;
            }

            for (Long groupId : groupIds) {

                if (!GroupConfigManager.isFeatureEnabled(groupId, "auto_sign")) {
                    continue;
                }

                sendGroupSign(groupId);
                log.info("群 {} 打卡成功", groupId);
            }
        } catch (Exception e) {
            log.warn("自动打卡异常", e);
        }
    }

    private static void sendGroupSign(long groupId) {
        PostRequest.sendSimplePost(CheckType.SEND_SIGN, groupId);
    }

    public static void processAutoSign() {
        Executors.newSingleThreadExecutor().submit(AutoSign::signAllGroups);
    }
}
