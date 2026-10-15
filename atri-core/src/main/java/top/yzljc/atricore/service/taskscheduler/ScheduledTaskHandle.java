package top.yzljc.atricore.service.taskscheduler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单个周期任务的取消句柄，取消后不再安排下一次执行
 *
 * @Author YZ_Ljc_
 * @ClassName ScheduledTaskHandle
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.taskscheduler
 */
public final class ScheduledTaskHandle implements AutoCloseable {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> currentFuture = new AtomicReference<>();

    boolean isCancelled() {
        return cancelled.get();
    }

    void replace(ScheduledFuture<?> future) {
        if (cancelled.get()) {
            future.cancel(false);
            return;
        }
        currentFuture.set(future);
    }

    /**
     * 取消后续调度，并按参数请求中断当前任务
     *
     * @param mayInterruptIfRunning 是否请求中断已经开始的执行
     * @return 当前 Future 的取消结果，没有已关联的 Future 时返回 false；返回值不表示任务一定已停止
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        cancelled.set(true);
        ScheduledFuture<?> future = currentFuture.get();
        return future != null && future.cancel(mayInterruptIfRunning);
    }

    /**
     * 取消后续调度，不中断已经开始的执行
     *
     */
    @Override
    public void close() {
        cancel(false);
    }
}
