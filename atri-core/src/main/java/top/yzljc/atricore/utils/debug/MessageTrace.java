package top.yzljc.atricore.utils.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息处理的本机计时上下文，关联异步任务并在全部任务结束后交付一次报告
 * <p>最多保留 48 个环节和 8 项附加信息，不保存消息正文、请求体或图片数据
 *
 * @Author YZ_Ljc_
 * @ClassName MessageTrace
 * @Created_at 2026/10/22
 * @Project AtriMeow
 * @Package top.yzljc.atricore.utils.debug
 */
public final class MessageTrace implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MessageTrace.class);
    private static final ThreadLocal<MessageTrace> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> OPERATION = new ThreadLocal<>();
    private static final Span NO_SPAN = new Span(null, "", 0);
    private final MessageTrace previous;
    private final String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final long started = System.nanoTime();
    private final Consumer<Report> receiver;
    private final List<Step> steps = new ArrayList<>();
    private final Map<String, String> information = new LinkedHashMap<>();
    private int pending = 1;
    private int omitted;
    private boolean failed;
    private boolean closed;
    private boolean delivered;

    private MessageTrace(Consumer<Report> receiver) {
        this.receiver = receiver;
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    /**
     * 开始一个独立的消息计时作用域，关闭时恢复原上下文
     *
     * @param receiver 全部关联任务结束后的报告接收者，不应执行阻塞操作
     * @return 必须在当前线程关闭的作用域
     */
    public static MessageTrace open(Consumer<Report> receiver) {
        return new MessageTrace(java.util.Objects.requireNonNull(receiver));
    }

    /**
     * 开始记录当前环节，未启用计时时返回无操作对象
     *
     * @param name 固定的环节名称，不应包含用户输入或请求地址
     * @return 环节计时器，必须关闭
     */
    public static Span span(String name) {
        MessageTrace trace = CURRENT.get();
        return trace == null ? NO_SPAN : new Span(trace, name, System.nanoTime());
    }

    /**
     * @return 当前线程是否关联了消息计时上下文
     */
    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    /**
     * 为作用域内的 QQ 接口请求指定业务环节名称，并传递至关联异步任务
     *
     * @param name 环节名称
     * @param action 业务操作
     * @param <T> 返回值类型
     * @return 原业务操作的结果
     */
    public static <T> T named(String name, Supplier<T> action) {
        if (!isActive()) return action.get();
        String previous = OPERATION.get();
        OPERATION.set(name);
        try { return action.get(); }
        finally { restoreOperation(previous); }
    }

    /**
     * @param fallback 未指定业务环节时使用的名称
     * @return 当前作用域的业务环节名称
     */
    public static String operationName(String fallback) {
        String name = OPERATION.get();
        return name == null ? fallback : name;
    }

    /**
     * 捕获提交时的上下文并保留一个待完成任务
     *
     * @param action 即将提交的任务
     * @param queueName 排队环节名称，null 表示不单独记录排队
     * @return 可跨线程执行一次的任务，未启用时返回 null；未提交或取消时必须调用 cancel
     */
    public static Task capture(Runnable action, String queueName) {
        MessageTrace trace = CURRENT.get();
        if (trace == null) return null;
        synchronized (trace) {
            if (trace.delivered) return null;
            trace.pending++;
        }
        return new Task(trace, action, queueName);
    }

    /**
     * 记录有限长度的附加信息，不接受 URL、凭据或消息正文
     *
     * @param key 信息名称
     * @param value 信息内容
     */
    public static void information(String key, String value) {
        MessageTrace trace = CURRENT.get();
        if (trace == null || value == null) return;
        synchronized (trace) {
            if (trace.information.size() < 8 || trace.information.containsKey(key))
                trace.information.put(clean(key, 32), clean(value, 128));
        }
    }

    /**
     * 标记本次链路包含执行失败，不记录异常正文
     */
    public static void failed() {
        MessageTrace trace = CURRENT.get();
        if (trace != null) synchronized (trace) { trace.failed = true; }
    }

    /**
     * 在无计时上下文的作用域中提交独立工作，结束后恢复当前上下文
     *
     * @param action 不属于消息处理链路的记录或报告操作
     */
    public static void detached(Runnable action) {
        MessageTrace previous = CURRENT.get();
        String previousOperation = OPERATION.get();
        CURRENT.remove();
        OPERATION.remove();
        try { action.run(); }
        finally { restore(previous); restoreOperation(previousOperation); }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        restore(previous);
        release();
    }

    private static void restore(MessageTrace trace) {
        if (trace == null) CURRENT.remove();
        else CURRENT.set(trace);
    }

    private static void restoreOperation(String name) {
        if (name == null) OPERATION.remove();
        else OPERATION.set(name);
    }

    private synchronized void add(String name, long start, long end, boolean success, String detail) {
        if (delivered) return;
        if (!success) failed = true;
        if (steps.size() < 48) steps.add(new Step(clean(name, 40), start - started,
                Math.max(0, end - start), success, clean(detail, 220)));
        else omitted++;
    }

    private void release() {
        Report report;
        synchronized (this) {
            if (--pending != 0 || delivered) return;
            delivered = true;
            report = new Report(id, Math.max(0, System.nanoTime() - started), failed,
                    steps.stream().sorted(Comparator.comparingLong(Step::offsetNanos)).toList(),
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(information)), omitted);
        }
        detached(() -> {
            try { receiver.accept(report); }
            catch (RuntimeException failure) { log.warn("消息链路报告交付失败: id={}", id, failure); }
        });
    }

    private static String clean(String value, int limit) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}]", " ");
        return text.substring(0, Math.min(text.length(), limit));
    }

    /**
     * @param name 环节名称
     * @param offsetNanos 相对链路开始的偏移，单位纳秒
     * @param durationNanos 实际耗时，单位纳秒
     * @param success 环节是否成功
     * @param detail 状态说明，不含请求正文
     */
    public record Step(String name, long offsetNanos, long durationNanos, boolean success, String detail) {}

    /**
     * @param id 本机生成的关联标识
     * @param durationNanos 从指令分发到全部关联任务结束的耗时，单位纳秒
     * @param failed 是否存在失败环节
     * @param steps 按开始时间排列的环节，不可修改
     * @param information 附加信息，不可修改
     * @param omitted 超出保留上限的环节数量
     */
    public record Report(String id, long durationNanos, boolean failed, List<Step> steps,
                         Map<String, String> information, int omitted) {
        /**
         * 判断不同环节的实际执行区间是否重叠
         *
         * @return 存在重叠时为 true，不能通过各环节耗时相加计算总耗时
         */
        public boolean overlapping() {
            long end = -1;
            for (Step step : steps) {
                if (step.offsetNanos() < end) return true;
                end = Math.max(end, step.offsetNanos() + step.durationNanos());
            }
            return false;
        }
    }

    public static final class Span implements AutoCloseable {
        private final MessageTrace trace;
        private final String name;
        private final long start;
        private boolean success = true;
        private String detail = "";
        private boolean closed;

        private Span(MessageTrace trace, String name, long start) {
            this.trace = trace;
            this.name = name;
            this.start = start;
        }

        /**
         * @param success 环节是否成功
         * @param detail 固定说明、HTTP 状态或业务码，不应包含凭据及用户输入
         */
        public void result(boolean success, String detail) {
            if (trace == null) return;
            this.success = success;
            this.detail = detail;
        }

        @Override
        public void close() {
            if (trace == null || closed) return;
            closed = true;
            trace.add(name, start, System.nanoTime(), success, detail);
        }
    }

    public static final class Task implements Runnable {
        private final MessageTrace trace;
        private final Runnable action;
        private final String queueName;
        private final String operationName;
        private final long submitted = System.nanoTime();
        private final AtomicInteger state = new AtomicInteger();

        private Task(MessageTrace trace, Runnable action, String queueName) {
            this.trace = trace;
            this.action = action;
            this.queueName = queueName;
            this.operationName = OPERATION.get();
        }

        @Override
        public void run() {
            if (!state.compareAndSet(0, 1)) return;
            MessageTrace previous = CURRENT.get();
            String previousOperation = OPERATION.get();
            CURRENT.set(trace);
            restoreOperation(operationName);
            long now = System.nanoTime();
            if (queueName != null && now - submitted >= TimeUnit.MILLISECONDS.toNanos(1))
                trace.add(queueName, submitted, now, true, "");
            try { action.run(); }
            catch (Throwable failure) {
                failed();
                throw failure;
            } finally {
                restore(previous);
                restoreOperation(previousOperation);
                state.set(2);
                trace.release();
            }
        }

        /**
         * 释放未开始的任务，不中断正在运行的任务
         *
         * @param reason 固定的取消或拒绝原因
         */
        public void cancel(String reason) {
            if (!state.compareAndSet(0, 2)) return;
            trace.add("任务未执行", submitted, System.nanoTime(), false, reason);
            trace.release();
        }
    }
}
