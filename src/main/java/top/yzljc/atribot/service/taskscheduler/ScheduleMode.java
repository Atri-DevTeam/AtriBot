package top.yzljc.atribot.service.taskscheduler;

public enum ScheduleMode {
    daily,
    hourly,
    half_hour,
    a_quarter,
    /** 每分钟执行，默认整分钟；time 可指定分钟内的秒及纳秒偏移。 */
    minutely
}
