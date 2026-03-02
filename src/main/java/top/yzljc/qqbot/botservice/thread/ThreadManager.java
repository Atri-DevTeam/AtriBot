package top.yzljc.qqbot.botservice.thread;

import java.util.concurrent.*;

public class ThreadManager {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private ThreadManager() {}

    public static ExecutorService getExecutor() {
        return EXECUTOR;
    }

    public static void execute(Runnable task) {
        EXECUTOR.submit(task);
    }

    public static Future<?> setExecute(Runnable task) {
        return EXECUTOR.submit(task);
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
}