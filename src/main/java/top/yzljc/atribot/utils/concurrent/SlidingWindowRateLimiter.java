package top.yzljc.atribot.utils.concurrent;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * @Author YZ_Ljc_
 * @ClassName SlidingWindowRateLimiter
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils.concurrent
 */
public final class SlidingWindowRateLimiter {
    private final int limit;
    private final long windowNanos;
    private final LongSupplier nanoTime;
    private final Deque<Long> timestamps = new ArrayDeque<>();

    /**
     * @param limit  单个滑动窗口内最多允许的操作次数，必须大于零
     * @param window 窗口长度，至少为一纳秒；使用单调时钟，不受系统时间调整影响
     */
    public SlidingWindowRateLimiter(int limit, Duration window) {
        this(limit, window, System::nanoTime);
    }

    SlidingWindowRateLimiter(int limit, Duration window, LongSupplier nanoTime) {
        if (limit <= 0) throw new IllegalArgumentException("限流次数必须大于零");
        windowNanos = Objects.requireNonNull(window, "window").toNanos();
        if (windowNanos <= 0) throw new IllegalArgumentException("限流窗口必须大于零");
        this.limit = limit;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /**
     * @return 有剩余额度时占用一次并返回 true，否则立即返回 false，不占用额度
     */
    public synchronized boolean tryAcquire() {
        return reserveOrDelay(nanoTime.getAsLong()) == 0;
    }

    /**
     * 阻塞至取得一次额度，多个调用线程之间不保证先后顺序。
     *
     * @throws InterruptedException 等待期间或调用前已被中断时抛出，不占用额度
     */
    public void acquire() throws InterruptedException {
        acquire(() -> false);
    }

    synchronized boolean acquire(BooleanSupplier cancelled) throws InterruptedException {
        while (!cancelled.getAsBoolean()) {
            if (Thread.interrupted()) throw new InterruptedException();
            long delay = reserveOrDelay(nanoTime.getAsLong());
            if (delay == 0) return true;
            TimeUnit.NANOSECONDS.timedWait(this, delay);
        }
        return false;
    }

    private long reserveOrDelay(long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowNanos) {
            timestamps.removeFirst();
        }
        if (timestamps.size() < limit) {
            timestamps.addLast(now);
            return 0;
        }
        return windowNanos - (now - timestamps.peekFirst());
    }
}
