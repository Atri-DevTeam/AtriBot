package top.yzljc.atribot.webui;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录入口采用独立的全局与来源地址滑动窗口，所有计数容器均有容量上限。
 *
 * @Author YZ_Ljc_
 * @ClassName WebUIAuthRateLimiter
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui
 */
public final class WebUIAuthRateLimiter {
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final int MAX_IP_BUCKETS = 256;
    private static final Decision ALLOWED = new Decision(true, 0);
    private static final WindowLimiter CHALLENGES = new WindowLimiter(20, 120);
    private static final WindowLimiter LOGINS = new WindowLimiter(10, 60);

    private WebUIAuthRateLimiter() {
    }

    public static Decision checkChallenge(String ip) {
        return CHALLENGES.tryAcquire(ip);
    }

    public static Decision checkLogin(String ip) {
        return LOGINS.tryAcquire(ip);
    }

    public record Decision(boolean allowed, int retryAfterSeconds) {
    }

    private static final class WindowLimiter {
        private final int perIpLimit;
        private final int globalLimit;
        private final ArrayDeque<Long> globalRequests = new ArrayDeque<>();
        // 按最后一次获准请求排序，仅在创建新桶且容量已满时清理过期桶。
        private final LinkedHashMap<String, ArrayDeque<Long>> ipRequests = new LinkedHashMap<>();

        private WindowLimiter(int perIpLimit, int globalLimit) {
            this.perIpLimit = perIpLimit;
            this.globalLimit = globalLimit;
        }

        private synchronized Decision tryAcquire(String ip) {
            long now = System.nanoTime();
            removeExpired(globalRequests, now);
            // 全局检查先于来源地址处理，伪造不同地址也不能突破全局额度。
            if (globalRequests.size() >= globalLimit) {
                return rejected(globalRequests.getFirst(), now);
            }

            String key = ip == null || ip.isBlank() || ip.length() > 128 ? "unknown" : ip;
            ArrayDeque<Long> requests = ipRequests.get(key);
            if (requests != null) {
                removeExpired(requests, now);
                if (requests.size() >= perIpLimit) return rejected(requests.getFirst(), now);
            } else {
                if (ipRequests.size() >= MAX_IP_BUCKETS) removeExpiredBuckets(now);
                if (ipRequests.size() >= MAX_IP_BUCKETS) {
                    return rejected(ipRequests.firstEntry().getValue().getLast(), now);
                }
                requests = new ArrayDeque<>();
            }

            globalRequests.addLast(now);
            requests.addLast(now);
            ipRequests.remove(key);
            ipRequests.put(key, requests);
            return ALLOWED;
        }

        private void removeExpiredBuckets(long now) {
            var entries = ipRequests.entrySet().iterator();
            while (entries.hasNext()) {
                ArrayDeque<Long> requests = entries.next().getValue();
                if (!requests.isEmpty() && now - requests.getLast() < WINDOW_NANOS) break;
                entries.remove();
            }
        }
    }

    private static void removeExpired(ArrayDeque<Long> requests, long now) {
        while (!requests.isEmpty() && now - requests.getFirst() >= WINDOW_NANOS) {
            requests.removeFirst();
        }
    }

    private static Decision rejected(long oldestRequest, long now) {
        long remaining = WINDOW_NANOS - (now - oldestRequest);
        int seconds = (int) Math.max(1, (remaining + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
        return new Decision(false, seconds);
    }
}
