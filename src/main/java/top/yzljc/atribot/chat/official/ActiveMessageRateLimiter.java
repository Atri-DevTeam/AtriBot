package top.yzljc.atribot.chat.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
final class ActiveMessageRateLimiter {

    private static final int ACTIVE_QPM_LIMIT = 60;
    private static final long ACTIVE_WINDOW_MS = 60_000;
    private static final int GROUP_ACTIVE_QPS_LIMIT = 100;
    private static final long GROUP_ACTIVE_WINDOW_MS = 1_000;
    private static final int PER_GROUP_ACTIVE_LIMIT = 5;
    private static final long PER_GROUP_WINDOW_MS = 60_000;

    private final Deque<Long> activeTimestamps = new ArrayDeque<>();
    private final Deque<Long> groupActiveSecondTimestamps = new ArrayDeque<>();
    private final Map<String, Deque<Long>> groupActiveTimestamps = new ConcurrentHashMap<>();

    void checkPerGroupActiveRate(String groupOpenId) {
        Deque<Long> timestamps = groupActiveTimestamps.computeIfAbsent(groupOpenId, ignored -> new ArrayDeque<>());
        int count;
        synchronized (timestamps) {
            long now = System.nanoTime();
            long cutoff = now - TimeUnit.MILLISECONDS.toNanos(PER_GROUP_WINDOW_MS);
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) timestamps.pollFirst();
            timestamps.offerLast(now);
            count = timestamps.size();
        }
        if (count >= PER_GROUP_ACTIVE_LIMIT) {
            Alert.notify("群聊主动消息频控异常：群 " + groupOpenId + " 在 1 分钟内发送了 " + count + " 条主动消息");
        }
    }

    void waitForActiveRateLimit() {
        waitForRateLimit(activeTimestamps, ACTIVE_QPM_LIMIT, ACTIVE_WINDOW_MS);
    }

    void waitForGroupActiveRateLimit() {
        waitForRateLimit(groupActiveSecondTimestamps, GROUP_ACTIVE_QPS_LIMIT, GROUP_ACTIVE_WINDOW_MS);
    }

    private synchronized void waitForRateLimit(Deque<Long> timestamps, int limit, long windowMs) {
        long windowNanos = TimeUnit.MILLISECONDS.toNanos(windowMs);
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("主动消息频控等待已中断");
            }
            long now = System.nanoTime();
            pruneExpired(timestamps, now - windowNanos);
            // 清理、检查与占用名额必须原子完成，唤醒后重新检查窗口。
            if (timestamps.size() < limit) {
                timestamps.offerLast(now);
                return;
            }
            long remaining = timestamps.peekFirst() + windowNanos - now;
            try {
                TimeUnit.NANOSECONDS.timedWait(this, Math.max(1, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("主动消息频控等待已中断");
            }
        }
    }

    private void pruneExpired(Deque<Long> timestamps, long cutoff) {
        while (true) {
            Long oldest = timestamps.peekFirst();
            if (oldest == null || oldest > cutoff) break;
            timestamps.pollFirst();
        }
    }
}
