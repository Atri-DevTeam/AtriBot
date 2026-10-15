package top.yzljc.atribot.utils.concurrent;

import org.junit.jupiter.api.Test;
import java.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitedTaskQueueTest {
    @Test
    void supportsDifferentResultTypesAndSharedLimits() throws Exception {
        var limiter = new SlidingWindowRateLimiter(1, Duration.ofMillis(60));
        var starts = new CopyOnWriteArrayList<Long>();
        try (var first = new RateLimitedTaskQueue("test-first", 2, limiter);
             var second = new RateLimitedTaskQueue("test-second", 2, limiter)) {
            assertEquals("message", first.submit(() -> {
                starts.add(System.nanoTime());
                return "message";
            }).get(3, TimeUnit.SECONDS));
            assertEquals(42, second.submit(() -> {
                starts.add(System.nanoTime());
                return 42;
            }).get(3, TimeUnit.SECONDS));
            assertTrue(starts.get(1) - starts.get(0) >= TimeUnit.MILLISECONDS.toNanos(55));
        }
    }

    @Test
    void limitsToTenPerSecondAndPreservesSubmissionOrder() throws Exception {
        var order = new CopyOnWriteArrayList<Integer>();
        var starts = new CopyOnWriteArrayList<Long>();
        List<CompletableFuture<Boolean>> results = new ArrayList<>();
        try (var queue = queue(32)) {
            for (int i = 0; i < 22; i++) {
                int index = i;
                results.add(queue.submit(() -> {
                    starts.add(System.nanoTime());
                    order.add(index);
                    return true;
                }));
            }
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).get(6, TimeUnit.SECONDS);
            assertEquals(IntStream.range(0, 22).boxed().toList(), order);
            assertTrue(results.stream().allMatch(CompletableFuture::join));
            for (int i = 10; i < starts.size(); i++) {
                assertTrue(starts.get(i) - starts.get(i - 10) >= TimeUnit.MILLISECONDS.toNanos(995));
            }
        }
    }

    @Test
    void boundsWaitingTasksAndCancellationReleasesQueueCapacity() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        long caller = Thread.currentThread().threadId();
        try (var queue = queue(1)) {
            var active = queue.submit(() -> {
                assertNotEquals(caller, Thread.currentThread().threadId());
                entered.countDown();
                return await(release);
            });
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                var cancelled = queue.submit(() -> fail("取消的任务不得执行"));
                assertRejected(queue.submit(() -> fail("队列满时不得在调用线程执行")));
                cancelled.cancel(false);
                var replacement = queue.submit(() -> false);
                release.countDown();
                assertTrue(active.get(3, TimeUnit.SECONDS));
                assertFalse(replacement.get(3, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void operationFailureDoesNotStopFollowingRequests() throws Exception {
        try (var queue = queue(4)) {
            var failure = queue.submit(() -> { throw new IllegalStateException("test failure"); });
            var next = queue.submit(() -> true);
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(ExecutionException.class, () -> failure.get(3, TimeUnit.SECONDS)).getCause());
            assertTrue(next.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void closingCompletesPendingRequestsAndRejectsNewOnes() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queue = queue(2);
        try {
            var active = queue.submit(() -> {
                entered.countDown();
                return await(release);
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var waiting = queue.submit(() -> fail("关闭后等待中的任务不得执行"));
            queue.close();
            assertRejected(waiting);
            assertRejected(queue.submit(() -> true));
            assertFalse(active.get(3, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            queue.close();
        }
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static RateLimitedTaskQueue queue(int capacity) {
        return new RateLimitedTaskQueue("test-rate-queue", capacity,
                new SlidingWindowRateLimiter(10, Duration.ofSeconds(1)));
    }

    private static void assertRejected(CompletableFuture<?> future) {
        assertInstanceOf(RejectedExecutionException.class,
                assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS)).getCause());
    }
}
