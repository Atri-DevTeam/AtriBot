package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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

    private final ArrayBlockingQueue<Event> queue;
    private final Executor executor;
    private final Consumer<Event> processor;
    // 排队、等待线程池空位和正在执行的事件均不按时间过期。
    private final Set<String> pendingIds = new HashSet<>();
    private final Cache<String, Boolean> completedIds = CacheBuilder.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();
    private final Thread dispatcher;
    private volatile boolean closed;

    public QQWebhookEventQueue(int capacity, Executor executor, Consumer<Event> processor) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.executor = executor;
        this.processor = processor;
        this.dispatcher = Thread.ofVirtual().name("qq-webhook-dispatcher").start(this::dispatch);
    }

    /** 非阻塞入队；重复事件视为已接收，拒收的事件不写入去重记录。 */
    synchronized boolean offer(Event event) {
        if (closed) {
            return false;
        }
        String id = event.id();
        boolean hasId = id != null && !id.isBlank();
        if (hasId && (pendingIds.contains(id) || completedIds.getIfPresent(id) != null)) {
            return true;
        }
        if (!queue.offer(event)) {
            return false;
        }
        if (hasId) {
            pendingIds.add(id);
        }
        return true;
    }

    private void dispatch() {
        while (!closed) {
            Event event;
            try {
                event = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                // 已向上游应答的事件不能因工作队列拥塞丢失。
                boolean logged = false;
                while (!closed) {
                    try {
                        executor.execute(() -> process(event));
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
                if (closed) forget(event);
            } catch (InterruptedException interrupted) {
                forget(event);
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                forget(event);
                if (!closed) {
                    log.error("QQ Webhook 事件提交失败: type={}, id={}", event.type(), event.id(), e);
                }
            }
        }
    }

    private void process(Event event) {
        if (closed) {
            log.warn("QQ Webhook 队列已关闭，取消尚未开始的事件: type={}, id={}", event.type(), event.id());
            return;
        }
        try {
            processor.accept(event);
        } catch (Exception e) {
            log.error("QQ Webhook 事件处理失败: type={}, id={}", event.type(), event.id(), e);
        } finally {
            complete(event);
        }
    }

    private synchronized void complete(Event event) {
        if (event.id() != null && !event.id().isBlank()) {
            if (!closed) {
                completedIds.put(event.id(), Boolean.TRUE);
            }
            pendingIds.remove(event.id());
        }
    }

    private synchronized void forget(Event event) {
        pendingIds.remove(event.id());
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        dispatcher.interrupt();
        int discarded = queue.size();
        queue.clear();
        pendingIds.clear();
        completedIds.invalidateAll();
        if (discarded > 0) {
            log.warn("QQ Webhook 内存队列关闭，丢弃 {} 条尚未提交的事件", discarded);
        }
    }
}
