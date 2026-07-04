package top.yzljc.atribot.service.taskscheduler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    public boolean cancel(boolean mayInterruptIfRunning) {
        cancelled.set(true);
        ScheduledFuture<?> future = currentFuture.get();
        return future != null && future.cancel(mayInterruptIfRunning);
    }

    @Override
    public void close() {
        cancel(false);
    }
}
