package top.yzljc.atribot.service.taskscheduler;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

public class TaskPlan implements TaskSchedule {
    private static final Duration HOURLY_LIMIT = Duration.ofHours(1);
    private static final Duration HALF_HOUR_LIMIT = Duration.ofMinutes(30);
    private static final Duration QUARTER_LIMIT = Duration.ofMinutes(15);

    private ScheduleMode mode;
    private LocalTime time = LocalTime.MIDNIGHT;

    public TaskPlan() {
    }

    public TaskPlan(ScheduleMode mode) {
        setMode(mode);
    }

    public TaskPlan(ScheduleMode mode, LocalTime time) {
        setMode(mode);
        setTime(time);
    }

    @Override
    public TaskSchedule setMode(ScheduleMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        validateTime(time, mode);
        return this;
    }

    @Override
    public TaskSchedule setTime(LocalTime time) {
        LocalTime nextTime = Objects.requireNonNull(time, "time");
        if (mode != null) {
            validateTime(nextTime, mode);
        }
        this.time = nextTime;
        return this;
    }

    @Override
    public ScheduleMode getMode() {
        if (mode == null) {
            throw new IllegalStateException("Schedule mode has not been set");
        }
        return mode;
    }

    @Override
    public LocalTime getTime() {
        return time;
    }

    private static void validateTime(LocalTime time, ScheduleMode mode) {
        long offsetNanos = time.toNanoOfDay();
        if (mode == ScheduleMode.hourly && offsetNanos >= HOURLY_LIMIT.toNanos()) {
            throw new IllegalArgumentException("hourly mode requires time before 01:00:00");
        }
        if (mode == ScheduleMode.half_hour && offsetNanos >= HALF_HOUR_LIMIT.toNanos()) {
            throw new IllegalArgumentException("half_hour mode requires time before 00:30:00");
        }
        if (mode == ScheduleMode.a_quarter && offsetNanos >= QUARTER_LIMIT.toNanos()) {
            throw new IllegalArgumentException("a_quarter mode requires time before 00:15:00");
        }
    }
}
