package top.yzljc.qqbot.botkits.clock;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.feature.schedule.*;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RunScheduleTask {
    private static final Logger log = LoggerFactory.getLogger(RunScheduleTask.class);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void runAllTasks() {
        // 每天 07:00:00
        scheduler.scheduleAtFixedRate(
                WakeUp::sendImgToGroup,
                computeInitialDelayByDay(7, 0, 0),
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
        // 每天 00:00:00
        scheduler.scheduleAtFixedRate(
                AutoSign::processAutoSign,
                computeInitialDelayByDay(0, 0, 0),
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
        // 每天 00:00:01
        scheduler.scheduleAtFixedRate(
                ManosabaDate::sendAndNotifyToGroup,
                computeInitialDelayByDay(0, 0, 1),
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
        // 每天 00:00:01
        scheduler.scheduleAtFixedRate(
                HappyNewYear::sendToAllGroups,
                computeInitialDelayByDay(0, 0, 1),
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
        // 每天 23:59:45
        scheduler.scheduleAtFixedRate(
                MessageStats::autoReportAllGroups,
                computeInitialDelayByDay(23, 59, 45),
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
        // 每小时 00 分 00 秒
        scheduler.scheduleAtFixedRate(
                () -> MinecraftNews.checkNews(false),
                computeInitialDelayByHour(0, 0),
                TimeUnit.HOURS.toSeconds(1),
                TimeUnit.SECONDS
        );
        // 每小时 00 分 00 秒
        scheduler.scheduleAtFixedRate(
                () -> HypixelNews.checkNews(false),
                computeInitialDelayByHour(0, 0),
                TimeUnit.HOURS.toSeconds(1),
                TimeUnit.SECONDS
        );
        log.info("所有定时任务已启动");
    }

    /**
     * 计算距离下一次 指定时:分:秒 的‘天定时’任务的延迟（单位：秒）
     */
    private static long computeInitialDelayByDay(int hour, int min, int sec) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(hour, min, sec);
        if (!now.isBefore(next)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toSeconds();
    }

    /**
     * 计算距离下一次 ‘小时准点定时’ 指定分:秒 的延迟（单位：秒）
     */
    private static long computeInitialDelayByHour(int minute, int second) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withMinute(minute).withSecond(second).withNano(0);
        if (!now.isBefore(next)) {
            next = next.plusHours(1);
        }
        return Duration.between(now, next).toSeconds();
    }
}