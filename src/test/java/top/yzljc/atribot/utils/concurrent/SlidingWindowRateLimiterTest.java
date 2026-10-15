package top.yzljc.atribot.utils.concurrent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowRateLimiterTest {
    @Test
    void expiresAtTheWindowBoundaryWithoutCountingRejectedAttempts() {
        var time = new AtomicLong(100);
        var limiter = new SlidingWindowRateLimiter(2, Duration.ofNanos(10), time::get);
        assertTrue(limiter.tryAcquire());
        time.set(105);
        assertTrue(limiter.tryAcquire());
        time.set(109);
        assertFalse(limiter.tryAcquire());
        time.set(110);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        time.set(115);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void permitsAreAtomicAcrossConcurrentCallers() throws Exception {
        var limiter = new SlidingWindowRateLimiter(7, Duration.ofMinutes(1), () -> 0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 100).mapToObj(_ -> (Callable<Boolean>) limiter::tryAcquire).toList();
            int allowed = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get()) allowed++;
            assertEquals(7, allowed);
        }
    }

    @Test
    void waitingCanBeInterrupted() throws Exception {
        var limiter = new SlidingWindowRateLimiter(1, Duration.ofDays(1));
        limiter.acquire();
        var entered = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        Thread waiter = Thread.ofVirtual().start(() -> {
            entered.countDown();
            try {
                limiter.acquire();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            waiter.interrupt();
            waiter.join(3000);
            assertFalse(waiter.isAlive());
            assertTrue(interrupted.get());
        } finally {
            waiter.interrupt();
        }
    }

    @Test
    void cancelledAndPreInterruptedCallsDoNotConsumePermits() throws Exception {
        var limiter = new SlidingWindowRateLimiter(1, Duration.ofSeconds(1), () -> 0);
        assertFalse(limiter.acquire(() -> true));
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, limiter::acquire);
        } finally {
            Thread.interrupted();
        }
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void validatesLimitsAndWindows() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowRateLimiter(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowRateLimiter(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowRateLimiter(1, Duration.ofNanos(-1)));
    }
}
