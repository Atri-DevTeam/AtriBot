package top.yzljc.atribot.chat.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
final class ActiveMessageRateLimiter {

    private static final int ACTIVE_QPM_LIMIT = 60;
    private static final long WINDOW_MS = 60_000;
    private static final int PER_GROUP_ACTIVE_LIMIT = 5;
    private static final long PER_GROUP_WINDOW_MS = 60_000;

    private final Deque<Long> activeTimestamps = new ConcurrentLinkedDeque<>();
    private final Map<String, Deque<Long>> groupActiveTimestamps = new ConcurrentHashMap<>();

    void checkPerGroupActiveRate(String groupOpenId) {
        Deque<Long> timestamps = groupActiveTimestamps.computeIfAbsent(groupOpenId, _ -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        long cutoff = now - PER_GROUP_WINDOW_MS;
        while (true) {
            Long oldest = timestamps.peekFirst();
            if (oldest == null || oldest >= cutoff) break;
            timestamps.pollFirst();
        }
        timestamps.offerLast(now);
        if (timestamps.size() >= PER_GROUP_ACTIVE_LIMIT) {
            Alert.notify("群聊主动消息频控异常：群 " + groupOpenId + " 在 1 分钟内发送了 " + timestamps.size() + " 条主动消息");
        }
    }

    void waitForActiveRateLimit() {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        while (true) {
            Long oldest = activeTimestamps.peekFirst();
            if (oldest == null || oldest >= cutoff) break;
            activeTimestamps.pollFirst();
        }

        if (activeTimestamps.size() >= ACTIVE_QPM_LIMIT) {
            Long oldest = activeTimestamps.peekFirst();
            if (oldest != null) {
                long waitMs = oldest + WINDOW_MS - now + 50;
                if (waitMs > 0) {
                    log.info("主动消息已达 {} QPM 限制，等待 {}ms", ACTIVE_QPM_LIMIT, waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    pruneExpired(System.currentTimeMillis() - WINDOW_MS);
                }
            }
        }
        activeTimestamps.offerLast(System.currentTimeMillis());
    }

    private void pruneExpired(long cutoff) {
        while (true) {
            Long oldest = activeTimestamps.peekFirst();
            if (oldest == null || oldest >= cutoff) break;
            activeTimestamps.pollFirst();
        }
    }
}
