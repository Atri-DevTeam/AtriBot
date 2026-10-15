package top.yzljc.atricore.service.taskscheduler;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 可配置的周期计划，每日模式使用具体时刻，其余模式使用周期内偏移
 *
 * @Author YZ_Ljc_
 * @ClassName TaskPlan
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.taskscheduler
 */
public class TaskPlan implements TaskSchedule {
    private static final Duration HOURLY_LIMIT = Duration.ofHours(1);
    private static final Duration HALF_HOUR_LIMIT = Duration.ofMinutes(30);
    private static final Duration QUARTER_LIMIT = Duration.ofMinutes(15);
    private static final Duration MINUTELY_LIMIT = Duration.ofMinutes(1);

    private ScheduleMode mode;
    private LocalTime time = LocalTime.MIDNIGHT;

    /**
     * 创建尚未设置周期类型的计划，注册前必须调用 setMode
     *
     */
    public TaskPlan() {
    }

    /**
     * 创建在周期起点执行的计划
     *
     * @param mode 周期类型
     */
    public TaskPlan(ScheduleMode mode) {
        setMode(mode);
    }

    /**
     * 创建指定周期及时刻的计划
     *
     * @param mode 周期类型
     * @param time 每日时刻或周期内偏移
     * @throws IllegalArgumentException 时间偏移超出周期范围
     * @throws NullPointerException 模式或时间为 null
     */
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
        if (mode == ScheduleMode.minutely && offsetNanos >= MINUTELY_LIMIT.toNanos()) {
            throw new IllegalArgumentException("minutely mode requires time before 00:01:00");
        }
    }
}
