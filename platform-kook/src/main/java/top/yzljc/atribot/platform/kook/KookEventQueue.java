package top.yzljc.atribot.platform.kook;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * @Author YZ_Ljc_
 * @ClassName KookEventQueue
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.kook
 */
@Slf4j
final class KookEventQueue implements AutoCloseable {
    private final int capacity;
    private final Executor executor;
    private final Consumer<JsonNode> processor;
    private final Set<String> pending = new HashSet<>();
    private final Cache<String, Boolean> completed = CacheBuilder.newBuilder()
            .maximumSize(20_000).expireAfterWrite(Duration.ofMinutes(10)).build();
    private boolean closed;

    KookEventQueue(int capacity, Executor executor, Consumer<JsonNode> processor) {
        if (capacity < 1) throw new IllegalArgumentException("KOOK 队列容量必须大于零");
        this.capacity = capacity;
        this.executor = executor;
        this.processor = processor;
    }

    synchronized boolean offer(JsonNode payload) {
        if (closed) return false;
        String key = payload.path("sn").asText() + ":" + payload.path("d").path("msg_id").asText();
        if (pending.contains(key) || completed.getIfPresent(key) != null) return true;
        if (pending.size() >= capacity) return false;
        pending.add(key);
        try {
            executor.execute(() -> process(key, payload));
            return true;
        } catch (RejectedExecutionException e) {
            pending.remove(key);
            return false;
        }
    }

    private void process(String key, JsonNode payload) {
        try {
            processor.accept(payload);
        } catch (RuntimeException e) {
            log.error("KOOK 事件处理失败: id={}, error={}", key, e.getClass().getSimpleName());
        } finally {
            synchronized (this) {
                pending.remove(key);
                completed.put(key, true);
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}

