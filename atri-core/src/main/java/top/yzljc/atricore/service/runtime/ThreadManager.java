package top.yzljc.atricore.service.runtime;

import top.yzljc.atricore.core.CoreConfig;
import top.yzljc.atricore.utils.debug.MessageTrace;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 进程共享的有界工作执行器与延迟任务入口
 * <p>执行并发与排队容量受框架配置限制，关闭后不能重新启动
 *
 * @Author YZ_Ljc_
 * @ClassName ThreadManager
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.runtime
 */
@Slf4j
public class ThreadManager {
    private static final int DEFAULT_MAX_CONCURRENT_TASKS = Math.max(
            16, Math.min(128, Runtime.getRuntime().availableProcessors() * 8));
    private static final int CONFIGURED_MAX_CONCURRENT_TASKS = CoreConfig.getInstance().getSettings().getThreadMaxConcurrency();
    private static final int MAX_CONCURRENT_TASKS = Math.max(1,
            Integer.getInteger("atribot.thread.maxConcurrency", CONFIGURED_MAX_CONCURRENT_TASKS == 0
                    ? DEFAULT_MAX_CONCURRENT_TASKS : CONFIGURED_MAX_CONCURRENT_TASKS));
    private static final int QUEUE_CAPACITY = Math.max(1,
            Integer.getInteger("atribot.thread.queueCapacity", CoreConfig.getInstance().getSettings().getThreadQueueCapacity()));
    private static final int SCHEDULE_CAPACITY = Math.max(1,
            Integer.getInteger("atribot.thread.scheduleCapacity", CoreConfig.getInstance().getSettings().getThreadScheduleCapacity()));
    // 定时容量只计算尚未转交工作池的任务；转交后由工作队列与工作线程数约束
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

    /**
     * 获取进程共享工作执行器
     *
     * @return 受并发与容量限制的执行器，业务模块不应单独关闭
     */
    public static ExecutorService getExecutor() {
        return EXECUTOR;
    }

    /**
     * 提交任务，不等待空闲工作槽位
     *
     * @param task 执行内容
     * @throws RejectedExecutionException 队列已满或执行器已关闭
     */
    public static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }

    /**
     * 提交可跟踪结果的任务并记录执行异常
     *
     * @param task 执行内容
     * @return 用于等待或取消任务的 Future
     * @throws RejectedExecutionException 队列已满或执行器已关闭
     */
    public static Future<?> setExecute(Runnable task) {
        return EXECUTOR.submit(logTask(task));
    }

    /**
     * 提交有返回值的任务并记录执行异常
     *
     * @param <T>  返回值类型
     * @param task 执行内容
     * @return 承载结果或异常的 Future
     * @throws RejectedExecutionException 队列已满或执行器已关闭
     */
    public static <T> Future<T> submit(Callable<T> task) {
        return EXECUTOR.submit(logTask(task));
    }

    /**
     * 提交可组合的任务，已在框架工作线程中时直接在当前线程执行
     *
     * @param <T>  返回值类型
     * @param task 执行内容
     * @return 承载结果的 Future，执行失败或提交被拒绝时以异常完成
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        // 同步聊天接口会立即等待此阶段；嵌套阶段复用当前工作槽位，避免父子互等
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

    /**
     * 提交一次延迟任务，不保留取消句柄
     *
     * @param task  执行内容
     * @param delay 延迟时长
     * @param unit  时间单位
     * @throws RejectedExecutionException 调度容量不足或线程服务已关闭
     */
    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        setSchedule(task, delay, unit);
    }

    /**
     * 提交一次延迟任务，到期后转交共享工作池
     *
     * @param task  执行内容
     * @param delay 延迟时长
     * @param unit  时间单位
     * @return 覆盖等待与实际执行阶段的任务句柄
     * @throws RejectedExecutionException 调度容量不足或线程服务已关闭
     */
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

    /**
     * 关闭共享线程服务并取消待调度任务，已运行的工作任务不强制中断
     * <p>仅在整个运行服务退出时调用，关闭后不能重新启动
     *
     */
    public static void shutdown() {
        if (!SHUTDOWN.compareAndSet(false, true)) return;
        SCHEDULER.shutdownNow();
        SCHEDULED_TASKS.forEach(task -> task.cancel(false));
        EXECUTOR.shutdown();
    }

    /**
     * 读取初始化时确定的并发上限
     *
     * @return 允许同时执行的工作任务数量
     */
    public static int getMaxConcurrentTasks() {
        return MAX_CONCURRENT_TASKS;
    }

    private static Runnable logTask(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable failure) {
                MessageTrace.failed();
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
                MessageTrace.failed();
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
                MessageTrace.failed();
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
        public void execute(Runnable task) {
            java.util.Objects.requireNonNull(task, "task");
            MessageTrace.Task traced = task instanceof ManagedFutureTask<?> ? null
                    : MessageTrace.capture(task, "工作队列等待");
            try {
                super.execute(traced == null ? task : traced);
            } catch (RejectedExecutionException failure) {
                if (traced != null) traced.cancel("工作队列已满或已关闭");
                if (task instanceof ManagedFutureTask<?> future && future.traced != null) future.cancel(false);
                throw failure;
            }
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
                if (task instanceof MessageTrace.Task traced) traced.cancel("工作线程池已关闭");
            });
            if (!pending.isEmpty()) log.warn("工作线程池关闭，取消 {} 个尚未执行的任务", pending.size());
            return pending;
        }

        private final class ManagedFutureTask<T> extends FutureTask<T> {
            private final MessageTrace.Task traced;

            private ManagedFutureTask(Callable<T> task) {
                super(task);
                traced = MessageTrace.capture(super::run, "工作队列等待");
            }

            @Override
            public void run() {
                if (traced == null) super.run();
                else traced.run();
            }

            @Override
            protected void done() {
                if (isCancelled() && traced != null) traced.cancel("工作任务已取消");
                if (traced != null && !isCancelled()) {
                    try { get(); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); MessageTrace.failed(); }
                    catch (ExecutionException failure) { MessageTrace.failed(); }
                }
            }

            @Override
            public T get() throws InterruptedException, ExecutionException {
                if (Thread.interrupted()) throw new InterruptedException();
                // 只有真正等待子任务时才取回执行；普通异步提交仍受队列和并发数限制
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

    /**
     * 队列拥塞时保留定时任务并稍后重试，调度线程只负责提交
     */
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
                    // 业务可能在 finally 中续排下一次任务，必须先归还定时容量
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
