package top.yzljc.atribot.service.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
public class ThreadManager {
    private static final int DEFAULT_MAX_CONCURRENT_TASKS = Math.max(
            16,
            Math.min(128, Runtime.getRuntime().availableProcessors() * 8)
    );
    private static final int MAX_CONCURRENT_TASKS = Integer.getInteger(
            "atribot.thread.maxConcurrency",
            DEFAULT_MAX_CONCURRENT_TASKS
    );
    private static final Semaphore TASK_PERMITS = new Semaphore(MAX_CONCURRENT_TASKS, true);
    private static final ThreadLocal<Boolean> MANAGED_TASK = ThreadLocal.withInitial(() -> false);

    private static final ThreadFactory VIRTUAL_THREAD_FACTORY = Thread.ofVirtual()
            .name("atribot-worker-", 0)
            .uncaughtExceptionHandler((thread, throwable) ->
                    log.error("虚拟线程任务未捕获异常, thread={}", thread.getName(), throwable))
            .factory();

    private static final ThreadFactory SCHEDULER_THREAD_FACTORY = Thread.ofPlatform()
            .name("atribot-scheduler-", 0)
            .daemon(false)
            .uncaughtExceptionHandler((thread, throwable) ->
                    log.error("调度线程未捕获异常, thread={}", thread.getName(), throwable))
            .factory();

    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newThreadPerTaskExecutor(VIRTUAL_THREAD_FACTORY);
    private static final ExecutorService EXECUTOR = new ManagedVirtualExecutorService();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(SCHEDULER_THREAD_FACTORY);

    public static ExecutorService getExecutor() {
        return EXECUTOR;
    }

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
        return CompletableFuture.supplyAsync(logTask(task), EXECUTOR);
    }

    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        SCHEDULER.schedule(() -> execute(task), delay, unit);
    }

    public static ScheduledFuture<?> setSchedule(Runnable task, long delay, TimeUnit unit) {
        return SCHEDULER.schedule(() -> execute(task), delay, unit);
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
        SCHEDULER.shutdown();
    }

    public static int getMaxConcurrentTasks() {
        return MAX_CONCURRENT_TASKS;
    }

    private static boolean acquirePermit() throws InterruptedException {
        if (MANAGED_TASK.get()) {
            return false;
        }
        TASK_PERMITS.acquire();
        return true;
    }

    private static Runnable wrapManagedTask(Runnable task, boolean releasePermit) {
        return () -> {
            boolean previous = MANAGED_TASK.get();
            MANAGED_TASK.set(true);
            try {
                task.run();
            } catch (Throwable throwable) {
                log.error("异步任务执行失败", throwable);
                throw throwable;
            } finally {
                MANAGED_TASK.set(previous);
                if (releasePermit) {
                    TASK_PERMITS.release();
                }
            }
        };
    }

    private static Runnable logTask(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                log.error("异步任务执行失败", throwable);
                throw throwable;
            }
        };
    }

    private static <T> Callable<T> logTask(Callable<T> task) {
        return () -> {
            try {
                return task.call();
            } catch (Throwable throwable) {
                log.error("异步任务执行失败", throwable);
                throw throwable;
            }
        };
    }

    private static <T> Supplier<T> logTask(Supplier<T> task) {
        return () -> {
            try {
                return task.get();
            } catch (Throwable throwable) {
                log.error("异步任务执行失败", throwable);
                throw throwable;
            }
        };
    }

    private static final class ManagedVirtualExecutorService extends AbstractExecutorService {
        private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

        @Override
        public void shutdown() {
            shuttingDown.set(true);
            VIRTUAL_EXECUTOR.shutdown();
        }

        @Override
        public @NonNull List<Runnable> shutdownNow() {
            shuttingDown.set(true);
            return VIRTUAL_EXECUTOR.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return shuttingDown.get() || VIRTUAL_EXECUTOR.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return VIRTUAL_EXECUTOR.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            return VIRTUAL_EXECUTOR.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(@NonNull Runnable command) {
            if (isShutdown()) {
                throw new RejectedExecutionException("ThreadManager is shut down");
            }

            boolean releasePermit = false;
            try {
                releasePermit = acquirePermit();
                VIRTUAL_EXECUTOR.execute(wrapManagedTask(command, releasePermit));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting for ThreadManager permit", e);
            } catch (RuntimeException e) {
                if (releasePermit) {
                    TASK_PERMITS.release();
                }
                throw e;
            }
        }
    }
}
