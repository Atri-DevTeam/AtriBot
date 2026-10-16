package top.yzljc.atricore.service.taskscheduler;

/**
 * 基于进程本地时间的周期类型
 *
 * @Author YZ_Ljc_
 * @ClassName ScheduleMode
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.taskscheduler
 */
public enum ScheduleMode {
    daily,
    hourly,
    half_hour,
    a_quarter,
    /**
     * 每分钟执行，默认整分钟；time 可指定分钟内的秒及纳秒偏移
     */
    minutely
}
