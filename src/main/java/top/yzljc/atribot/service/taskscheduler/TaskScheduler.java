package top.yzljc.atribot.service.taskscheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TaskScheduler implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final List<ScheduledTaskHandle> handles = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, ScheduledTaskHandle> scheduledTaskHandles = new ConcurrentHashMap<>();

    public ScheduledTaskHandle schedule(ScheduledTask task) {
        Objects.requireNonNull(task, "task");
        return scheduledTaskHandles.computeIfAbsent(task.getClass(), ignored -> schedule(task, task.schedule()));
    }

    public ScheduledTaskHandle schedule(Runnable task, TaskSchedule schedule) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(schedule, "schedule");

        ScheduledTaskHandle handle = new ScheduledTaskHandle();
        handles.add(handle);
        scheduleNext(task, schedule, handle);
        return handle;
    }

    public void shutdown() {
        handles.forEach(handle -> handle.cancel(false));
        handles.clear();
        scheduledTaskHandles.clear();
    }

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
