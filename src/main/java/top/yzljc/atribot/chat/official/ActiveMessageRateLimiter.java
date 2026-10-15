package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.utils.concurrent.SlidingWindowRateLimiter;
import top.yzljc.atribot.utils.tools.Alert;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class ActiveMessageRateLimiter {

    private static final int PER_GROUP_ACTIVE_LIMIT = 5;
    private static final long PER_GROUP_WINDOW_MS = 60_000;

    private final SlidingWindowRateLimiter activeLimiter = new SlidingWindowRateLimiter(60, Duration.ofMinutes(1));
    private final SlidingWindowRateLimiter groupActiveLimiter = new SlidingWindowRateLimiter(100, Duration.ofSeconds(1));
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
        waitForRateLimit(activeLimiter);
    }

    void waitForGroupActiveRateLimit() {
        waitForRateLimit(groupActiveLimiter);
    }

    private void waitForRateLimit(SlidingWindowRateLimiter limiter) {
        try {
            limiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("主动消息频控等待已中断");
        }
    }
}
