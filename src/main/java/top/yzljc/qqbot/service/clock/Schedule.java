package top.yzljc.qqbot.service.clock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
