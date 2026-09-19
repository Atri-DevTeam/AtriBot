package top.yzljc.atribot.platform.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 有界接收回调，同一群或私聊按接收顺序处理，其他会话可以并行。
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatEventQueue
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.napcat
 */
@Slf4j
public final class NapcatEventQueue implements AutoCloseable {
    private static final int CAPACITY = Math.max(1,
            Integer.getInteger("atribot.napcat.queueCapacity", 1024));
    private static final long BYTE_CAPACITY = Math.max(1,
            Long.getLong("atribot.napcat.queueBytes", 16L * 1024 * 1024));
    private static final int WORKERS = Math.max(1, Math.min(64,
            Integer.getInteger("atribot.napcat.workers", 8)));

    private final Map<String, ArrayDeque<QueuedEvent>> sessions = new HashMap<>();
    private final ArrayDeque<String> readySessions = new ArrayDeque<>();
    private final Set<String> pendingIds = new HashSet<>();
    private final Cache<String, Boolean> completedIds = CacheBuilder.newBuilder()
            .maximumSize(20_000).expireAfterWrite(Duration.ofMinutes(10)).build();
    private final Set<Future<?>> running = ConcurrentHashMap.newKeySet();
    private final List<Thread> workers = new ArrayList<>();
    private final CountDownLatch stopped = new CountDownLatch(WORKERS);
    private boolean accepting = true;
    private volatile boolean stopping;
    private int pendingCount;
    private long pendingBytes;
    private long lastRejectionLog;

    public NapcatEventQueue() {
        for (int i = 0; i < WORKERS; i++) {
            workers.add(Thread.ofVirtual().name("napcat-dispatcher-" + i).start(this::dispatch));
        }
    }

    /** 拒收时不登记去重记录，调用方可返回 503 让上游重试。 */
    public synchronized boolean offer(JsonNode payload, int payloadBytes) {
        if (!accepting) return false;
        String id = eventId(payload);
        if (id != null && (pendingIds.contains(id) || completedIds.getIfPresent(id) != null)) return true;
        if (pendingCount >= CAPACITY || payloadBytes > BYTE_CAPACITY - pendingBytes) {
            long now = System.nanoTime();
            if (lastRejectionLog == 0 || now - lastRejectionLog > TimeUnit.SECONDS.toNanos(1)) {
                lastRejectionLog = now;
                log.warn("NapCat 回调队列已满，返回 503 等待上游重试: pending={}, bytes={}", pendingCount, pendingBytes);
            }
            return false;
        }

        String session = sessionId(payload);
        ArrayDeque<QueuedEvent> queue = sessions.get(session);
        if (queue == null) {
            queue = new ArrayDeque<>();
            sessions.put(session, queue);
            readySessions.addLast(session);
        }
        queue.addLast(new QueuedEvent(session, id, payload, payloadBytes));
        pendingCount++;
        pendingBytes += payloadBytes;
        if (id != null) pendingIds.add(id);
        notifyAll();
        return true;
    }

    private synchronized QueuedEvent take() throws InterruptedException {
        while (readySessions.isEmpty()) {
            if (!accepting || stopping) return null;
            wait();
        }
        if (stopping) return null;
        return sessions.get(readySessions.removeFirst()).removeFirst();
    }

    private void dispatch() {
        try {
            while (!stopping) {
                QueuedEvent event = take();
                if (event == null) return;
                boolean processed = false;
                try {
                    Future<?> future = submit(event);
                    if (future == null) return;
                    running.add(future);
                    if (stopping) future.cancel(true);
                    try {
                        future.get();
                        processed = true;
                    } finally {
                        running.remove(future);
                    }
                } catch (ExecutionException | CancellationException failure) {
                    log.error("NapCat 事件处理失败: session={}, id={}", event.session(), event.id(), failure);
                } finally {
                    complete(event, processed);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            stopped.countDown();
        }
    }

    private Future<?> submit(QueuedEvent event) throws InterruptedException {
        boolean logged = false;
        while (!stopping) {
            try {
                return ThreadManager.setExecute(() -> RequestReceiver.handle(event.payload()));
            } catch (RejectedExecutionException failure) {
                if (ThreadManager.getExecutor().isShutdown()) {
                    log.error("工作线程池关闭，NapCat 已接收事件未能执行: session={}, id={}",
                            event.session(), event.id());
                    return null;
                }
                if (!logged) {
                    log.warn("工作队列已满，保留 NapCat 已接收事件等待执行: session={}", event.session());
                    logged = true;
                }
                Thread.sleep(100);
            }
        }
        return null;
    }

    private synchronized void complete(QueuedEvent event, boolean processed) {
        if (event.id() != null) {
            pendingIds.remove(event.id());
            if (processed && !stopping) completedIds.put(event.id(), Boolean.TRUE);
        }
        if (stopping) return;
        pendingCount--;
        pendingBytes -= event.payloadBytes();
        ArrayDeque<QueuedEvent> queue = sessions.get(event.session());
        if (queue == null || queue.isEmpty()) {
            sessions.remove(event.session());
        } else {
            readySessions.addLast(event.session());
        }
        notifyAll();
    }

    private static String sessionId(JsonNode payload) {
        String self = payload.path("self_id").asText("");
        String group = payload.path("group_id").asText("");
        if (!group.isBlank()) return self + ":group:" + group;
        return self + ":private:" + payload.path("user_id").asText("system");
    }

    private static String eventId(JsonNode payload) {
        String type = payload.path("post_type").asText("");
        String identifier = switch (type) {
            case "message", "message_sent" -> payload.path("message_id").asText("");
            case "request" -> payload.path("flag").asText("");
            default -> "";
        };
        return identifier.isBlank() ? null : sessionId(payload) + ":" + type + ":" + identifier;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (!accepting) return;
            accepting = false;
            notifyAll();
        }
        try {
            if (stopped.await(10, TimeUnit.SECONDS)) return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            stopping = true;
            log.warn("NapCat 队列关闭超时，取消 {} 条尚未完成的事件", pendingCount);
            sessions.clear();
            readySessions.clear();
            pendingIds.clear();
            completedIds.invalidateAll();
            notifyAll();
        }
        running.forEach(future -> future.cancel(true));
        workers.forEach(Thread::interrupt);
    }

    private record QueuedEvent(String session, String id, JsonNode payload, int payloadBytes) {}
}
