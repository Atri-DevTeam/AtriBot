package top.yzljc.atribot.service.timer;

import java.lang.annotation.*;

/**
 * 标注在无参静态方法上，表示定时任务。同一方法可标注多个，表示在多个时间点执行。
 * <ul>
 *   <li>DAILY: time 为 "HH:mm:ss"，如 "07:00:00"</li>
 *   <li>HOURLY: time 为 "mm:ss"，如 "00:00"</li>
 * </ul>
 */
@Repeatable(Schedules.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Schedule {
    String time();
    ScheduleType type();
}
