package top.yzljc.atricore.service.taskscheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 计算周期计划的下一执行时点及相对延迟
 *
 * @Author YZ_Ljc_
 * @ClassName TaskScheduleTimes
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.taskscheduler
 */
public final class TaskScheduleTimes {
    private TaskScheduleTimes() {
    }

    /**
     * 计算严格晚于当前基准时间的下一执行时点
     *
     * @param schedule 周期计划
     * @return 下一执行时点，恰好位于边界时选择下一个周期
     */
    public static LocalDateTime nextRunTime(TaskSchedule schedule) {
        return nextRunTime(schedule, LocalDateTime.now());
    }

    /**
     * 计算严格晚于当前基准时间的下一执行时点
     *
     * @param schedule 周期计划
     * @param now      用于计算的本地时间
     * @return 下一执行时点，恰好位于边界时选择下一个周期
     */
    public static LocalDateTime nextRunTime(TaskSchedule schedule, LocalDateTime now) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(now, "now");

        ScheduleMode mode = schedule.getMode();
        LocalTime time = schedule.getTime();
        return switch (mode) {
            case daily -> nextDaily(now, time);
            case hourly -> nextHourly(now, time);
            case half_hour -> nextHalfHour(now, time);
            case a_quarter -> nextQuarter(now, time);
            case minutely -> nextMinutely(now, time);
        };
    }

    /**
     * 按进程本地时间计算下一执行延迟
     *
     * @param schedule 周期计划
     * @return 距离下一执行时点的纳秒数
     */
    public static long delayNanos(TaskSchedule schedule) {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, nextRunTime(schedule, now)).toNanos();
    }

    private static LocalDateTime nextDaily(LocalDateTime now, LocalTime time) {
        LocalDateTime next = now.toLocalDate().atTime(time);
        if (!now.isBefore(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private static LocalDateTime nextMinutely(LocalDateTime now, LocalTime time) {
        LocalDateTime next = now.truncatedTo(ChronoUnit.MINUTES).plusNanos(time.toNanoOfDay());
        if (!now.isBefore(next)) {
            next = next.plusMinutes(1);
        }
        return next;
    }

    private static LocalDateTime nextHourly(LocalDateTime now, LocalTime time) {
        LocalDateTime next = now.truncatedTo(ChronoUnit.HOURS).plusNanos(time.toNanoOfDay());
        if (!now.isBefore(next)) {
            next = next.plusHours(1);
        }
        return next;
    }

    private static LocalDateTime nextHalfHour(LocalDateTime now, LocalTime time) {
        LocalDateTime base = now.truncatedTo(ChronoUnit.HOURS);
        if (now.getMinute() >= 30) {
            base = base.plusMinutes(30);
        }

        LocalDateTime next = base.plusNanos(time.toNanoOfDay());
        if (!now.isBefore(next)) {
            next = next.plusMinutes(30);
        }
        return next;
    }

    private static LocalDateTime nextQuarter(LocalDateTime now, LocalTime time) {
        LocalDateTime base = now.truncatedTo(ChronoUnit.HOURS)
                .plusMinutes((now.getMinute() / 15L) * 15L);

        LocalDateTime next = base.plusNanos(time.toNanoOfDay());
        if (!now.isBefore(next)) {
            next = next.plusMinutes(15);
        }
        return next;
    }
}
