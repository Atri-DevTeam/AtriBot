package top.yzljc.atricore.service.taskscheduler;

/**
 * 具有周期计划的业务任务，执行内容由 Runnable.run 提供
 *
 * @Author YZ_Ljc_
 * @ClassName ScheduledTask
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.taskscheduler
 */
public interface ScheduledTask extends Runnable {
    /**
     * 提供任务的周期计划
     *
     * @return 非 null 的调度计划；注册后不应并发修改
     */
    TaskSchedule schedule();
}
