package top.yzljc.atricore.service.taskscheduler;

import java.time.LocalTime;

/**
 * 周期计划契约，时间的含义由调度模式决定
 *
 * @Author YZ_Ljc_
 * @ClassName TaskSchedule
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.taskscheduler
 */
public interface TaskSchedule {
    /**
     * 设置周期类型
     *
     * @param mode 周期类型
     * @return 当前计划
     * @throws IllegalArgumentException 现有时间偏移超出新周期范围
     */
    TaskSchedule setMode(ScheduleMode mode);

    /**
     * 设置每日时刻或周期内偏移
     *
     * @param time 每日模式为本地时刻，其余模式为小于一个周期的偏移
     * @return 当前计划
     * @throws IllegalArgumentException 偏移超出当前周期范围
     */
    TaskSchedule setTime(LocalTime time);

    /**
     * 将时刻或周期内偏移重置为零
     *
     * @return 当前计划
     */
    default TaskSchedule setTime() {
        return setTime(LocalTime.MIDNIGHT);
    }

    /**
     * 获取周期类型
     *
     * @return 已设置的周期类型
     * @throws IllegalStateException 尚未设置周期类型
     */
    ScheduleMode getMode();

    /**
     * 获取计划的时间部分
     *
     * @return 每日时刻或周期内偏移
     */
    LocalTime getTime();
}
