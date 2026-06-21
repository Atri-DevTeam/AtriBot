package top.yzljc.atribot.service;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Scheduler {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform()
                    .name("atribot-service-scheduler-", 0)
                    .daemon(false)
                    .uncaughtExceptionHandler((thread, throwable) ->
                            log.error("服务调度线程未捕获异常, thread={}", thread.getName(), throwable))
                    .factory()
    );

    public ScheduledFuture<?> runTask(Runnable run) {
        return scheduler.schedule(() -> dispatch(run, "runTask"), 0, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> runTaskAsynchronously(Runnable run) {
        return runTask(run);
    }

    /**
     * @param delayMillis 延迟毫秒数
     */
    public ScheduledFuture<?> runTaskLater(Runnable run, long delayMillis) {
        return scheduler.schedule(() -> dispatch(run, "runTaskLater"), delayMillis, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> runTaskLaterAsynchronously(Runnable run, long delayMillis) {
        return runTaskLater(run, delayMillis);
    }

    /**
     * @param delayMillis 初始延迟毫秒数
     * @param periodMillis 周期毫秒数
     */
    public ScheduledFuture<?> runTaskTimer(Runnable run, long delayMillis, long periodMillis) {
        AtomicBoolean running = new AtomicBoolean(false);
        return scheduler.scheduleAtFixedRate(
                () -> dispatchNonOverlapping(run, running, "runTaskTimer"),
                delayMillis,
                periodMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public ScheduledFuture<?> runTaskTimerAsynchronously(Runnable run, long delayMillis, long periodMillis) {
        return runTaskTimer(run, delayMillis, periodMillis);
    }

    public void cancelTask(ScheduledFuture<?> taskId) {
        if (taskId != null) {
            taskId.cancel(false);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void dispatch(Runnable run, String taskType) {
        ThreadManager.execute(() -> runSafely(run, taskType));
    }

    private void dispatchNonOverlapping(Runnable run, AtomicBoolean running, String taskType) {
        if (!running.compareAndSet(false, true)) {
            log.warn("{} 上一次任务尚未结束，跳过本次触发", taskType);
            return;
        }

        ThreadManager.execute(() -> {
            try {
                runSafely(run, taskType);
            } finally {
                running.set(false);
            }
        });
    }

    private void runSafely(Runnable run, String taskType) {
        try {
            run.run();
        } catch (Throwable throwable) {
            log.error("{} 执行失败", taskType, throwable);
        }
    }
}


