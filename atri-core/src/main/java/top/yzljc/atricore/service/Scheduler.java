package top.yzljc.atricore.service;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用共享线程服务的即时、延迟与固定间隔调度器
 * <p>所有任务均交给工作线程执行，名称带 Asynchronously 的方法是对应方法的别名
 *
 * @Author YZ_Ljc_
 * @ClassName Scheduler
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service
 */
@Slf4j
public class Scheduler {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<TaskHandle> tasks = ConcurrentHashMap.newKeySet();

    /**
     * 提交即时任务，由共享工作线程执行
     *
     * @param task 执行内容
     * @return 用于跟踪结果或取消的任务句柄
     * @throws RejectedExecutionException 当前调度器已关闭
     */
    public ScheduledFuture<?> runTask(Runnable task) {
        return schedule(task, 0, 0);
    }

    /**
     * 提交即时任务，与 runTask 行为相同
     *
     * @param task 执行内容
     * @return 用于跟踪结果或取消的任务句柄
     * @throws RejectedExecutionException 当前调度器已关闭
     */
    public ScheduledFuture<?> runTaskAsynchronously(Runnable task) {
        return runTask(task);
    }

    /**
     * 提交一次延迟任务
     *
     * @param task        执行内容
     * @param delayMillis 延迟毫秒数，负数按零处理
     * @return 用于跟踪结果或取消的任务句柄
     * @throws RejectedExecutionException 当前调度器已关闭
     */
    public ScheduledFuture<?> runTaskLater(Runnable task, long delayMillis) {
        return schedule(task, delayMillis, 0);
    }

    /**
     * 提交一次延迟任务，与 runTaskLater 行为相同
     *
     * @param task        执行内容
     * @param delayMillis 延迟毫秒数，负数按零处理
     * @return 用于跟踪结果或取消的任务句柄
     * @throws RejectedExecutionException 当前调度器已关闭
     */
    public ScheduledFuture<?> runTaskLaterAsynchronously(Runnable task, long delayMillis) {
        return runTaskLater(task, delayMillis);
    }

    /**
     * 按计划间隔重复执行，执行超时则跳过错过的时点
     *
     * @param task         执行内容
     * @param delayMillis  首次执行延迟，单位为毫秒
     * @param periodMillis 重复执行间隔，单位为毫秒，必须大于零
     * @return 用于跟踪结果或取消的任务句柄
     * @throws RejectedExecutionException 当前调度器已关闭
     */
    public ScheduledFuture<?> runTaskTimer(Runnable task, long delayMillis, long periodMillis) {
        if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis must be positive");
        return schedule(task, delayMillis, periodMillis);
    }

    /**
     * 提交重复任务，与 runTaskTimer 行为相同
     *
     * @param task         执行内容
     * @param delayMillis  首次执行延迟，单位为毫秒
     * @param periodMillis 重复执行间隔，单位为毫秒，必须大于零
     * @return 用于跟踪结果或取消的任务句柄
     * @throws RejectedExecutionException 当前调度器已关闭
     */
    public ScheduledFuture<?> runTaskTimerAsynchronously(Runnable task, long delayMillis, long periodMillis) {
        return runTaskTimer(task, delayMillis, periodMillis);
    }

    /**
     * 取消指定任务，不中断已经开始的执行
     *
     * @param task 待取消句柄，为 null 时不执行操作
     */
    public void cancelTask(ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
    }

    /**
     * 取消当前调度器的任务并拒绝新注册，不关闭共享线程服务
     *
     */
    public void shutdown() {
        closed.set(true);
        tasks.forEach(task -> task.cancel(false));
    }

    private ScheduledFuture<?> schedule(Runnable task, long delayMillis, long periodMillis) {
        if (closed.get()) throw new RejectedExecutionException("Scheduler is shut down");
        TaskHandle handle = new TaskHandle(task, Math.max(0, delayMillis), periodMillis);
        tasks.add(handle);
        if (closed.get()) {
            handle.cancel(false);
            throw new RejectedExecutionException("Scheduler is shut down");
        }
        handle.scheduleNext();
        return handle;
    }

    /**
     * 句柄跟踪实际执行与拥塞重试，取消后不能再触发业务任务
     */
    private final class TaskHandle implements ScheduledFuture<Void>, Runnable {
        private final Runnable task;
        private final long periodNanos;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile ScheduledFuture<?> current;
        private volatile boolean interruptOnCancel;
        private long nextRun;

        private TaskHandle(Runnable task, long delayMillis, long periodMillis) {
            this.task = java.util.Objects.requireNonNull(task, "task");
            periodNanos = TimeUnit.MILLISECONDS.toNanos(periodMillis);
            nextRun = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
            completion.whenComplete((ignored, failure) -> tasks.remove(this));
        }

        private synchronized void scheduleNext() {
            if (completion.isDone() || closed.get()) {
                cancel(false);
                return;
            }
            try {
                ScheduledFuture<?> future = ThreadManager.setSchedule(this,
                        Math.max(0, nextRun - System.nanoTime()), TimeUnit.NANOSECONDS);
                current = future;
                if (completion.isCancelled()) future.cancel(interruptOnCancel);
            } catch (RejectedExecutionException failure) {
                completion.completeExceptionally(failure);
                log.warn("服务定时任务未能提交", failure);
            }
        }

        @Override
        public void run() {
            synchronized (this) {
                if (completion.isDone() || closed.get()) return;
            }
            try {
                task.run();
            } catch (Throwable failure) {
                log.error("服务定时任务执行失败", failure);
            } finally {
                if (periodNanos == 0) {
                    completion.complete(null);
                } else if (!completion.isDone() && !closed.get()) {
                    nextRun += periodNanos;
                    long now = System.nanoTime();
                    // 执行时间超过周期时跳过错过的时点，避免重叠或集中补跑
                    if (nextRun < now) {
                        long skipped = (now - nextRun) / periodNanos + 1;
                        nextRun += skipped * periodNanos;
                        log.warn("定时任务执行较慢，跳过 {} 次触发", skipped);
                    }
                    scheduleNext();
                }
            }
        }

        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            interruptOnCancel = mayInterruptIfRunning;
            boolean result = completion.cancel(mayInterruptIfRunning);
            ScheduledFuture<?> future = current;
            if (future != null) future.cancel(mayInterruptIfRunning);
            return result;
        }

        @Override
        public boolean isCancelled() {
            return completion.isCancelled();
        }

        @Override
        public boolean isDone() {
            return completion.isDone();
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            return completion.get();
        }

        @Override
        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return completion.get(timeout, unit);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            ScheduledFuture<?> future = current;
            return future == null ? 0 : future.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
