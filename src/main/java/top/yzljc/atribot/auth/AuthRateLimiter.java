package top.yzljc.atribot.auth;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * 有界的固定窗口频控；桶满时拒绝新增来源。
 *
 * @Author YZ_Ljc_
 * @ClassName AuthRateLimiter
 * @Created_at 2026/09/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
final class AuthRateLimiter {
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    AuthRateLimiter(Clock clock) { this.clock = clock; }

    synchronized boolean acquire(String key, int limit) {
        long now = clock.millis();
        windows.values().removeIf(window -> now >= window.expiresAt);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 4096) return false;
            window = new Window(now + 60_000);
            windows.put(key, window);
        }
        if (window.count >= limit) return false;
        window.count++;
        return true;
    }

    private static final class Window {
        final long expiresAt;
        int count;
        Window(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
