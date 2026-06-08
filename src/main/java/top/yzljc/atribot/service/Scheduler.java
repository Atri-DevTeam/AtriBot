package top.yzljc.atribot.service;

import java.util.concurrent.*;

public class Scheduler {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ScheduledFuture<?> runTask(Runnable run) {
        return scheduler.schedule(run, 0, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> runTaskAsynchronously(Runnable run) {
        return scheduler.schedule(() -> ThreadManager.execute(run), 0, TimeUnit.MILLISECONDS);
    }

    /**
     * @param delayMillis 延迟毫秒数
     */
    public ScheduledFuture<?> runTaskLater(Runnable run, long delayMillis) {
        return scheduler.schedule(run, delayMillis, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> runTaskLaterAsynchronously(Runnable run, long delayMillis) {
        return scheduler.schedule(() -> ThreadManager.execute(run), delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * @param delayMillis 初始延迟毫秒数
     * @param periodMillis 周期毫秒数
     */
    public ScheduledFuture<?> runTaskTimer(Runnable run, long delayMillis, long periodMillis) {
        return scheduler.scheduleAtFixedRate(run, delayMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> runTaskTimerAsynchronously(Runnable run, long delayMillis, long periodMillis) {
        return scheduler.scheduleAtFixedRate(() -> ThreadManager.execute(run), delayMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    public void cancelTask(ScheduledFuture<?> taskId) {
        if (taskId != null) {
            taskId.cancel(false);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}


