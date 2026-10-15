package top.yzljc.atricore.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 事件处理方法标记，方法须声明一个具体事件类型的参数
 *
 * @Author YZ_Ljc_
 * @ClassName EventHandler
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {

    /**
     * 指定处理方法的执行顺序
     *
     * @return 优先级，默认 NORMAL；MONITOR 不强制只读
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * 指定是否跳过已经取消的事件
     *
     * @return true 表示跳过，默认 false；仅对 Cancellable 事件生效
     */
    boolean ignoreCancelled() default false;
}
