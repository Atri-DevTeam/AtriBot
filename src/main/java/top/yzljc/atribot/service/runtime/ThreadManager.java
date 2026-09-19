package top.yzljc.atribot.service.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
public class ThreadManager {
    private static final int DEFAULT_MAX_CONCURRENT_TASKS = Math.max(
            16, Math.min(128, Runtime.getRuntime().availableProcessors() * 8));
    private static final int MAX_CONCURRENT_TASKS = Math.max(1,
            Integer.getInteger("atribot.thread.maxConcurrency", DEFAULT_MAX_CONCURRENT_TASKS));
    private static final int QUEUE_CAPACITY = Math.max(1,
            Integer.getInteger("atribot.thread.queueCapacity", 4096));
    private static final int SCHEDULE_CAPACITY = Math.max(1,
            Integer.getInteger("atribot.thread.scheduleCapacity", 4096));
    // 定时容量只计算尚未转交工作池的任务；转交后由工作队列与工作线程数约束。
    private static final Semaphore SCHEDULE_PERMITS = new Semaphore(SCHEDULE_CAPACITY);
    private static final ThreadLocal<Boolean> MANAGED_TASK = ThreadLocal.withInitial(() -> false);
    private static final AtomicLong LAST_REJECTION_LOG = new AtomicLong();
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean();
    private static final Set<ScheduledDispatch> SCHEDULED_TASKS = ConcurrentHashMap.newKeySet();
    private static final ManagedVirtualExecutorService EXECUTOR = new ManagedVirtualExecutorService();
    private static final ScheduledThreadPoolExecutor SCHEDULER = createScheduler();

    private static ScheduledThreadPoolExecutor createScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1,
                Thread.ofPlatform().name("atribot-scheduler-", 0).daemon(false).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }

    public static ExecutorService getExecutor() {
        return EXECUTOR;
    }

    /** 提交不等待工作槽位；队列已满时由调用方处理明确的拒绝异常。 */
    public static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }

    public static Future<?> setExecute(Runnable task) {
        return EXECUTOR.submit(logTask(task));
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return EXECUTOR.submit(logTask(task));
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        // 同步聊天接口会立即等待此阶段；嵌套阶段复用当前工作槽位，避免父子互等。
        if (MANAGED_TASK.get()) {
            try {
                return CompletableFuture.completedFuture(logTask(task).get());
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        try {
            return CompletableFuture.supplyAsync(logTask(task), EXECUTOR);
        } catch (RejectedExecutionException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        setSchedule(task, delay, unit);
    }

    public static ScheduledFuture<?> setSchedule(Runnable task, long delay, TimeUnit unit) {
        java.util.Objects.requireNonNull(task, "task");
        if (SHUTDOWN.get()) throw new RejectedExecutionException("ThreadManager is shut down");
        if (!SCHEDULE_PERMITS.tryAcquire()) {
            log.warn("定时任务已达容量上限，拒绝新任务: capacity={}", SCHEDULE_CAPACITY);
            throw new RejectedExecutionException("ThreadManager scheduled task capacity exceeded");
        }
        ScheduledDispatch dispatch = new ScheduledDispatch(task);
        SCHEDULED_TASKS.add(dispatch);
        try {
            dispatch.schedule(delay, unit);
            if (SHUTDOWN.get()) dispatch.cancel(false);
            return dispatch;
        } catch (RuntimeException failure) {
            dispatch.completion.completeExceptionally(failure);
            throw failure;
        }
    }

    public static void shutdown() {
        if (!SHUTDOWN.compareAndSet(false, true)) return;
        SCHEDULER.shutdownNow();
        SCHEDULED_TASKS.forEach(task -> task.cancel(false));
        EXECUTOR.shutdown();
    }

    public static int getMaxConcurrentTasks() {
        return MAX_CONCURRENT_TASKS;
    }

    private static Runnable logTask(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable failure) {
                log.error("异步任务执行失败", failure);
                throw failure;
            }
        };
    }

    private static <T> Callable<T> logTask(Callable<T> task) {
        return () -> {
            try {
                return task.call();
            } catch (Throwable failure) {
                log.error("异步任务执行失败", failure);
                throw failure;
            }
        };
    }

    private static <T> Supplier<T> logTask(Supplier<T> task) {
        return () -> {
            try {
                return task.get();
            } catch (Throwable failure) {
                log.error("异步任务执行失败", failure);
                throw failure;
            }
        };
    }

    private static final class ManagedVirtualExecutorService extends ThreadPoolExecutor {
        private ManagedVirtualExecutorService() {
            super(MAX_CONCURRENT_TASKS, MAX_CONCURRENT_TASKS, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                    Thread.ofVirtual().name("atribot-worker-", 0).factory(),
                    (task, executor) -> {
                        long now = System.nanoTime();
                        long previous = LAST_REJECTION_LOG.get();
                        if ((previous == 0 || now - previous > TimeUnit.SECONDS.toNanos(1))
                                && LAST_REJECTION_LOG.compareAndSet(previous, now)) {
                            log.warn("工作任务被拒绝: shutdown={}, active={}, queued={}, capacity={}",
                                    executor.isShutdown(), executor.getActiveCount(),
                                    executor.getQueue().size(), QUEUE_CAPACITY);
                        }
                        throw new RejectedExecutionException("ThreadManager is shut down or its queue is full");
                    });
        }

        @Override
        protected void beforeExecute(Thread thread, Runnable task) {
            MANAGED_TASK.set(true);
        }

        @Override
        protected void afterExecute(Runnable task, Throwable failure) {
            MANAGED_TASK.remove();
            if (failure != null) log.error("异步任务执行失败", failure);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Callable<T> task) {
            return new ManagedFutureTask<>(task);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Runnable task, T result) {
            return new ManagedFutureTask<>(Executors.callable(task, result));
        }

        @Override
        public @NonNull List<Runnable> shutdownNow() {
            List<Runnable> pending = super.shutdownNow();
            pending.forEach(task -> {
                if (task instanceof Future<?> future) future.cancel(false);
            });
            if (!pending.isEmpty()) log.warn("工作线程池关闭，取消 {} 个尚未执行的任务", pending.size());
            return pending;
        }

        private final class ManagedFutureTask<T> extends FutureTask<T> {
            private ManagedFutureTask(Callable<T> task) {
                super(task);
            }

            @Override
            public T get() throws InterruptedException, ExecutionException {
                if (Thread.interrupted()) throw new InterruptedException();
                // 只有真正等待子任务时才取回执行；普通异步提交仍受队列和并发数限制。
                if (MANAGED_TASK.get() && remove(this)) run();
                return super.get();
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean cancelled = super.cancel(mayInterruptIfRunning);
                if (cancelled) remove(this);
                return cancelled;
            }
        }
    }

    /** 队列拥塞时保留定时任务并稍后重试，调度线程只负责提交。 */
    private static final class ScheduledDispatch implements ScheduledFuture<Void>, Runnable {
        private final Runnable task;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final AtomicBoolean congestionLogged = new AtomicBoolean();
        private final AtomicBoolean schedulePermitHeld = new AtomicBoolean(true);
        private volatile ScheduledFuture<?> trigger;
        private volatile Future<?> running;
        private volatile boolean interruptOnCancel;

        private ScheduledDispatch(Runnable task) {
            this.task = java.util.Objects.requireNonNull(task, "task");
            completion.whenComplete((ignored, failure) -> {
                SCHEDULED_TASKS.remove(this);
                releaseSchedulePermit();
            });
        }

        private void releaseSchedulePermit() {
            if (schedulePermitHeld.compareAndSet(true, false)) SCHEDULE_PERMITS.release();
        }

        private synchronized void schedule(long delay, TimeUnit unit) {
            ScheduledFuture<?> next = SCHEDULER.schedule(this, delay, unit);
            trigger = next;
            if (completion.isDone()) next.cancel(false);
        }

        @Override
        public void run() {
            synchronized (this) {
                if (completion.isDone()) return;
            }
            try {
                Future<?> submitted = EXECUTOR.submit(() -> {
                    // 业务可能在 finally 中续排下一次任务，必须先归还定时容量。
                    releaseSchedulePermit();
                    if (completion.isDone()) return;
                    try {
                        task.run();
                        completion.complete(null);
                    } catch (Throwable failure) {
                        log.error("定时任务执行失败", failure);
                        completion.completeExceptionally(failure);
                    }
                });
                running = submitted;
                releaseSchedulePermit();
                if (completion.isCancelled()) submitted.cancel(interruptOnCancel);
            } catch (RejectedExecutionException failure) {
                if (EXECUTOR.isShutdown() || SCHEDULER.isShutdown()) {
                    completion.completeExceptionally(failure);
                    log.warn("线程池关闭，定时任务未能提交", failure);
                    return;
                }
                if (congestionLogged.compareAndSet(false, true)) {
                    log.warn("工作队列已满，定时任务延迟执行");
                }
                try {
                    schedule(1, TimeUnit.SECONDS);
                } catch (RejectedExecutionException stopped) {
                    completion.completeExceptionally(stopped);
                }
            }
        }

        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            interruptOnCancel = mayInterruptIfRunning;
            boolean cancelled = completion.cancel(mayInterruptIfRunning);
            ScheduledFuture<?> scheduled = trigger;
            if (scheduled != null) scheduled.cancel(false);
            Future<?> submitted = running;
            if (submitted != null) submitted.cancel(mayInterruptIfRunning);
            return cancelled;
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
            ScheduledFuture<?> scheduled = trigger;
            return scheduled == null ? 0 : scheduled.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
