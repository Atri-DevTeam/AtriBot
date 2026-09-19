package top.yzljc.atribot.service;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Scheduler {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<TaskHandle> tasks = ConcurrentHashMap.newKeySet();

    public ScheduledFuture<?> runTask(Runnable task) {
        return schedule(task, 0, 0);
    }

    public ScheduledFuture<?> runTaskAsynchronously(Runnable task) {
        return runTask(task);
    }

    public ScheduledFuture<?> runTaskLater(Runnable task, long delayMillis) {
        return schedule(task, delayMillis, 0);
    }

    public ScheduledFuture<?> runTaskLaterAsynchronously(Runnable task, long delayMillis) {
        return runTaskLater(task, delayMillis);
    }

    public ScheduledFuture<?> runTaskTimer(Runnable task, long delayMillis, long periodMillis) {
        if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis must be positive");
        return schedule(task, delayMillis, periodMillis);
    }

    public ScheduledFuture<?> runTaskTimerAsynchronously(Runnable task, long delayMillis, long periodMillis) {
        return runTaskTimer(task, delayMillis, periodMillis);
    }

    public void cancelTask(ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
    }

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

    /** 句柄跟踪实际执行与拥塞重试，取消后不能再触发业务任务。 */
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
                    // 执行时间超过周期时跳过错过的时点，避免重叠或集中补跑。
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
