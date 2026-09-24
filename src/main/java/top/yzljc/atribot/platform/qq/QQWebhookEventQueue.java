package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @Author YZ_Ljc_
 * @ClassName QQWebhookEventQueue
 * @Created_at 2026/09/12
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.qq
 */
@Slf4j
final class QQWebhookEventQueue implements AutoCloseable {
    record Event(String type, String id, JsonNode data, String rawPayload) { }

    private final ArrayBlockingQueue<PendingEvent> queue;
    private final Semaphore capacity;
    private final Executor executor;
    private final Consumer<Event> processor;
    // 预留位置、等待 ACK、排队及执行中的事件均不按时间过期。
    private final Map<String, PendingEvent> pendingIds = new HashMap<>();
    private final Set<PendingEvent> pendingEvents = new HashSet<>();
    private final Cache<String, Boolean> completedIds = CacheBuilder.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();
    private final Thread dispatcher;
    private volatile boolean closed;

    public QQWebhookEventQueue(int capacity, Executor executor, Consumer<Event> processor) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.capacity = new Semaphore(capacity);
        this.executor = executor;
        this.processor = processor;
        this.dispatcher = Thread.ofVirtual().name("qq-webhook-dispatcher").start(this::dispatch);
    }

    /** 只预留容量，不分发；每次 HTTP 投递都必须报告它自己的 ACK 结果。 */
    synchronized Receipt reserve(Event event) {
        if (closed) {
            return null;
        }
        String id = event.id();
        boolean hasId = id != null && !id.isBlank();
        if (hasId && completedIds.getIfPresent(id) != null) {
            return new Receipt(null);
        }
        PendingEvent pending = hasId ? pendingIds.get(id) : null;
        if (pending != null) {
            pending.receipts++;
            return new Receipt(pending);
        }
        if (!capacity.tryAcquire()) return null;

        pending = new PendingEvent(event);
        pendingEvents.add(pending);
        if (hasId) {
            pendingIds.put(id, pending);
        }
        return new Receipt(pending);
    }

    final class Receipt {
        private final PendingEvent pending;
        private final AtomicBoolean resolved = new AtomicBoolean();

        private Receipt(PendingEvent pending) {
            this.pending = pending;
        }

        void acknowledge() {
            if (pending != null && resolved.compareAndSet(false, true)) resolve(pending, true);
        }

        void fail() {
            if (pending != null && resolved.compareAndSet(false, true)) resolve(pending, false);
        }
    }

    private static final class PendingEvent {
        private final Event event;
        private int receipts = 1;
        private boolean acknowledged;
        private boolean capacityReleased;

        private PendingEvent(Event event) {
            this.event = event;
        }
    }

    private synchronized void resolve(PendingEvent pending, boolean acknowledged) {
        if (closed) return;
        pending.receipts--;
        if (pending.acknowledged) return;
        if (acknowledged) {
            pending.acknowledged = true;
            // 预留容量覆盖所有尚未交给工作线程的事件，此处必定有空间。
            queue.add(pending);
        } else if (pending.receipts == 0) {
            forget(pending);
        }
    }

    private void dispatch() {
        while (!closed) {
            PendingEvent pending;
            try {
                pending = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Event event = pending.event;
            try {
                // 已向上游应答的事件不能因工作队列拥塞丢失。
                boolean logged = false;
                while (!closed) {
                    try {
                        executor.execute(() -> process(pending));
                        releaseCapacity(pending);
                        break;
                    } catch (RejectedExecutionException full) {
                        if (!logged) {
                            log.warn("工作队列暂不可用，保留 QQ Webhook 事件等待执行: type={}, id={}",
                                    event.type(), event.id());
                            logged = true;
                        }
                        TimeUnit.MILLISECONDS.sleep(100);
                    }
                }
                if (closed) forget(pending);
            } catch (InterruptedException interrupted) {
                forget(pending);
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                forget(pending);
                if (!closed) {
                    log.error("QQ Webhook 事件提交失败: type={}, id={}", event.type(), event.id(), e);
                }
            }
        }
    }

    private void process(PendingEvent pending) {
        Event event = pending.event;
        if (closed) {
            log.warn("QQ Webhook 队列已关闭，取消尚未开始的事件: type={}, id={}", event.type(), event.id());
            return;
        }
        try {
            processor.accept(event);
        } catch (Exception e) {
            log.error("QQ Webhook 事件处理失败: type={}, id={}", event.type(), event.id(), e);
        } finally {
            complete(pending);
        }
    }

    private synchronized void complete(PendingEvent pending) {
        Event event = pending.event;
        if (event.id() != null && !event.id().isBlank()) {
            if (!closed) {
                completedIds.put(event.id(), Boolean.TRUE);
            }
        }
        forget(pending);
    }

    private synchronized void forget(PendingEvent pending) {
        pendingIds.remove(pending.event.id(), pending);
        pendingEvents.remove(pending);
        releaseCapacity(pending);
    }

    private synchronized void releaseCapacity(PendingEvent pending) {
        if (!pending.capacityReleased) {
            pending.capacityReleased = true;
            capacity.release();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        dispatcher.interrupt();
        int discarded = 0;
        for (PendingEvent pending : pendingEvents) {
            if (!pending.capacityReleased) discarded++;
            releaseCapacity(pending);
        }
        queue.clear();
        pendingIds.clear();
        pendingEvents.clear();
        completedIds.invalidateAll();
        if (discarded > 0) {
            log.warn("QQ Webhook 内存队列关闭，丢弃 {} 条尚未提交的事件", discarded);
        }
    }
}
