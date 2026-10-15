package top.yzljc.atricore.utils.concurrent;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @Author YZ_Ljc_
 * @ClassName RateLimitedTaskQueue
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.utils.concurrent
 */
public final class RateLimitedTaskQueue implements AutoCloseable {
    private final SlidingWindowRateLimiter limiter;
    private final ThreadPoolExecutor executor;

    /**
     * @param threadName 工作线程名称
     * @param capacity   等待队列容量，不包含当前执行的一个任务，必须大于零
     * @param limiter    操作执行前使用的限流器；复用同一实例时共享额度
     */
    public RateLimitedTaskQueue(String threadName, int capacity, SlidingWindowRateLimiter limiter) {
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity),
                Thread.ofPlatform().daemon(true).name(threadName).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    /**
     * 按 FIFO 顺序串行执行，调用线程不等待额度或队列空位
     *
     * @param operation 仅在队列工作线程执行的同步操作
     * @param <T>       操作结果类型
     * @return 操作结果；队列满或关闭时异常完成。取消可跳过尚未执行的操作，不中断已开始的操作
     */
    public <T> CompletableFuture<T> submit(Supplier<T> operation) {
        Request<T> request = new Request<>(Objects.requireNonNull(operation, "operation"));
        request.result.whenComplete((ignored, failure) -> {
            if (request.result.isCancelled()) executor.remove(request);
        });
        try {
            executor.execute(request);
        } catch (RejectedExecutionException e) {
            request.result.completeExceptionally(e);
        }
        return request.result;
    }

    /**
     * 中断工作线程并拒绝新任务，使尚在队列中的任务异常完成
     */
    @Override
    public void close() {
        for (Runnable pending : executor.shutdownNow()) {
            if (pending instanceof RateLimitedTaskQueue.Request<?> request) {
                request.result.completeExceptionally(new RejectedExecutionException("限速任务队列已关闭"));
            }
        }
    }

    private final class Request<T> implements Runnable {
        private final Supplier<T> operation;
        private final CompletableFuture<T> result = new CompletableFuture<>();

        private Request(Supplier<T> operation) {
            this.operation = operation;
        }

        @Override
        public void run() {
            try {
                if (limiter.acquire(result::isDone) && !result.isDone()) result.complete(operation.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(e);
            } catch (Throwable e) {
                result.completeExceptionally(e);
                if (e instanceof Error error) throw error;
            }
        }
    }
}
