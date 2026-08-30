package top.yzljc.sakuraba_ema.groups;

import lombok.extern.slf4j.Slf4j;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Per-instance active-message limiter. */
@Slf4j
final class GroupBotRateLimiter {

    private static final int ACTIVE_QPM_LIMIT = 60;
    private static final long WINDOW_MS = 60_000;

    private final Deque<Long> activeTimestamps = new ConcurrentLinkedDeque<>();

    void awaitPermit() {
        long now = System.currentTimeMillis();
        prune(now - WINDOW_MS);
        if (activeTimestamps.size() >= ACTIVE_QPM_LIMIT) {
            Long oldest = activeTimestamps.peekFirst();
            if (oldest != null) {
                long waitMs = oldest + WINDOW_MS - now + 50;
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.debug("等待 QQ 群聊主动消息频控时被中断");
                    }
                    prune(System.currentTimeMillis() - WINDOW_MS);
                }
            }
        }
        activeTimestamps.offerLast(System.currentTimeMillis());
    }

    private void prune(long cutoff) {
        while (true) {
            Long oldest = activeTimestamps.peekFirst();
            if (oldest == null || oldest >= cutoff) {
                return;
            }
            activeTimestamps.pollFirst();
        }
    }
}
