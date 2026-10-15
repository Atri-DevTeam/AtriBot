package top.yzljc.atricore.service.taskscheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于本地时间边界的周期调度器，由 Provider 管理共享实例
 * <p>每次执行结束后安排下一次任务，不补跑已经错过的时点
 *
 * @Author YZ_Ljc_
 * @ClassName TaskScheduler
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.taskscheduler
 */
public final class TaskScheduler implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private boolean closed;
    private final List<ScheduledTaskHandle> handles = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, ScheduledTaskHandle> scheduledTaskHandles = new ConcurrentHashMap<>();

    /**
     * 注册按任务类去重的周期任务
     * <p>同一任务类再次注册返回原句柄，原句柄取消后也不会自动创建新任务
     *
     * @param task 业务任务
     * @return 任务取消句柄
     * @throws IllegalStateException 调度器已经关闭
     * @throws NullPointerException 任务或其计划为 null
     * @throws java.util.concurrent.RejectedExecutionException 共享调度容量不足或线程服务已关闭
     */
    public synchronized ScheduledTaskHandle schedule(ScheduledTask task) {
        if (closed) throw new IllegalStateException("计划调度器已关闭");
        Objects.requireNonNull(task, "task");
        return scheduledTaskHandles.computeIfAbsent(task.getClass(), ignored -> schedule(task, task.schedule()));
    }

    /**
     * 注册独立的周期任务，此重载不按任务类去重
     *
     * @param task 业务执行内容
     * @param schedule 周期计划，任务存续期间不应并发修改
     * @return 任务取消句柄
     * @throws IllegalStateException 调度器已经关闭
     * @throws NullPointerException 任务或计划为 null
     * @throws java.util.concurrent.RejectedExecutionException 共享调度容量不足或线程服务已关闭
     */
    public synchronized ScheduledTaskHandle schedule(Runnable task, TaskSchedule schedule) {
        if (closed) throw new IllegalStateException("计划调度器已关闭");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(schedule, "schedule");

        ScheduledTaskHandle handle = new ScheduledTaskHandle();
        handles.add(handle);
        scheduleNext(task, schedule, handle);
        return handle;
    }

    /**
     * 取消全部任务并拒绝后续注册，重复调用不重复处理
     * <p>不强制中断已经开始的执行，也不关闭进程共享线程服务
     *
     */
    public synchronized void shutdown() {
        if (closed) return;
        closed = true;
        handles.forEach(handle -> handle.cancel(false));
        handles.clear();
        scheduledTaskHandles.clear();
    }

    /**
     * 关闭调度器，行为与 shutdown 相同
     *
     */
    @Override
    public void close() {
        shutdown();
    }

    private void scheduleNext(Runnable task, TaskSchedule schedule, ScheduledTaskHandle handle) {
        if (handle.isCancelled()) {
            return;
        }

        long delayNanos = TaskScheduleTimes.delayNanos(schedule);
        handle.replace(ThreadManager.setSchedule(() -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                log.error("定时任务执行异常", throwable);
            } finally {
                scheduleNext(task, schedule, handle);
            }
        }, delayNanos, TimeUnit.NANOSECONDS));
    }
}
