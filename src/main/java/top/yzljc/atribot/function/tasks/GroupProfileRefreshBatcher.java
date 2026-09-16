package top.yzljc.atribot.function.tasks;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupProfileRefreshBatcher
 * @Created_at 2026/09/12
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks
 */
@Slf4j
public final class GroupProfileRefreshBatcher implements AutoCloseable {
    private static final long COOLDOWN_MILLIS = 10_000;

    private final BiConsumer<Runnable, Long> schedule;
    private final Consumer<String> refresh;
    private final Set<String> pendingGroups = new LinkedHashSet<>();
    private boolean scheduled;
    private boolean closed;

    public GroupProfileRefreshBatcher(BiConsumer<Runnable, Long> schedule, Consumer<String> refresh) {
        this.schedule = schedule;
        this.refresh = refresh;
    }

    public synchronized void request(String groupOpenId) {
        if (closed || groupOpenId == null || groupOpenId.isBlank()) {
            return;
        }
        pendingGroups.add(groupOpenId);
        scheduleIfNeeded();
    }

    // 调用时持有锁；整批任务只占一个调度项，不为每条成员事件创建任务。
    private void scheduleIfNeeded() {
        if (closed || scheduled || pendingGroups.isEmpty()) {
            return;
        }
        scheduled = true;
        try {
            schedule.accept(this::refreshBatch, COOLDOWN_MILLIS);
        } catch (RuntimeException e) {
            scheduled = false;
            log.error("调度成员变动后的群资料集中刷新失败", e);
        }
    }

    private void refreshBatch() {
        List<String> groups;
        synchronized (this) {
            if (closed) {
                return;
            }
            groups = List.copyOf(pendingGroups);
        }
        try {
            for (String groupOpenId : groups) {
                synchronized (this) {
                    if (closed) {
                        return;
                    }
                    // 真正开始刷新时再移出，同批尚未轮到的群仍可合并变动。
                    if (!pendingGroups.remove(groupOpenId)) {
                        continue;
                    }
                }
                try {
                    refresh.accept(groupOpenId);
                } catch (Exception e) {
                    log.error("成员变动后的群资料刷新失败，群ID: {}", groupOpenId, e);
                }
            }
        } finally {
            synchronized (this) {
                scheduled = false;
                // 刷新期间的新变动留到下一轮，既不并发刷新，也不丢弃尾部变动。
                scheduleIfNeeded();
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        pendingGroups.clear();
    }
}
