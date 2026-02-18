package top.yzljc.qqbot.botkits.clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.command.Reboot;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.feature.schedule.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RunScheduleTask {
    private static final Logger log = LoggerFactory.getLogger(RunScheduleTask.class);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    public static void runAllTasks() {
        // 每天 07:00:00
        scheduleDailyTask(WakeUp::sendImgToGroup, 7, 0, 0);

        // 每天 00:00:00
        scheduleDailyTask(AutoSign::processAutoSign, 0, 0, 1);

        scheduleDailyTask(ManosabaDate::sendAndNotifyToGroup, 0, 0, 10);
        // scheduleDailyTask(HappyNewYear::sendToAllGroups, 0, 0, 11);
        scheduleDailyTask(Calendar::sendToAllGroups, 0, 0, 25);

        // 每天 23:59:45
        scheduleDailyTask(MessageStats::autoReportAllGroups, 23, 59, 45);

        scheduleDailyTask(Reboot::processReboot, 5, 20, 0);

        // 每小时 00 分 00 秒
        scheduleHourlyTask(() -> MinecraftNews.checkNews(false), 0, 0);
        scheduleHourlyTask(() -> HypixelNews.checkNews(false), 0, 0);

        log.info("所有定时任务已启动");
    }

    private static void scheduleDailyTask(Runnable task, int hour, int min, int sec) {
        long delayMillis = computeInitialDelayByDayMillis(hour, min, sec);

        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("定时任务执行异常", e);
            } finally {
                scheduleDailyTask(task, hour, min, sec);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static void scheduleHourlyTask(Runnable task, int minute, int second) {
        long delayMillis = computeInitialDelayByHourMillis(minute, second);

        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("整点任务执行异常", e);
            } finally {
                scheduleHourlyTask(task, minute, second);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static long computeInitialDelayByDayMillis(int hour, int min, int sec) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(hour, min, sec);
        if (!now.isBefore(next)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    private static long computeInitialDelayByHourMillis(int minute, int second) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withMinute(minute).withSecond(second).withNano(0);
        if (!now.isBefore(next)) {
            next = next.plusHours(1);
        }
        return Duration.between(now, next).toMillis();
    }
}