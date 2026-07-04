package top.yzljc.atribot.service.taskscheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class TaskScheduleTimes {
    private TaskScheduleTimes() {
    }

    public static LocalDateTime nextRunTime(TaskSchedule schedule) {
        return nextRunTime(schedule, LocalDateTime.now());
    }

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
        };
    }

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
