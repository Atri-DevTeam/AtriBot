package top.yzljc.atribot.function.tasks.pushtask;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.Atri;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.repo.GroupDailyReportRepository;
import top.yzljc.atribot.database.repo.GroupDailyReportRepository.Report;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupDailyReport
 * @Created_at 2026/10/14
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.task
 */
public final class GroupDailyReportTask extends PushTask implements ScheduledTask {
    private static final Logger log = LoggerFactory.getLogger(GroupDailyReportTask.class);

    private GroupDailyReportTask() {
        super("group_daily_report", "昨日群发言排行", true, true, false);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("总结昨日群里活跃情况，展示最活跃的10名小伙伴\n\n" + getStatus(platform, platformIdentifyId));
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan().setMode(ScheduleMode.daily).setTime(LocalTime.of(0, 0));
    }

    @Override
    public void run() {

    }

    private static final class Holder {
        private static final Worker WORKER = new Worker(128, 500,
                Clock.system(GroupDailyReportRepository.ZONE), GroupDailyReportTask::send);
    }

    /**
     * @param groupOpenId 接收排行的官机群 OpenID
     * @return 异步发送结果；采用主动消息发送，结果语义见 {@link #sendYesterday(String, RT)}
     */
    public static CompletableFuture<String> sendYesterday(String groupOpenId) {
        return sendYesterday(groupOpenId, null);
    }

    /**
     * 提交时确定北京时间的昨日日期，后台串行查询并发送，最多等待 128 个任务。
     * 相同群和日期的在途请求共用任务，采用首次提交的回复来源。成功及空结果最多缓存
     * 4096 项、保留 36 小时，仅用于当前进程去重；重启或缓存淘汰后可再次发送。
     * 不自动重试；取消返回的 Future 不会取消已入队任务。
     *
     * @param groupOpenId 官机群 OpenID
     * @param replyTarget 被动回复来源，null 表示主动发送；排队不会延长平台的回复有效期
     * @return 发送成功返回消息 ID，无消息记录返回 null；队列满、查询或发送失败时异常完成
     * @throws IllegalArgumentException 群 OpenID 为空时抛出
     */
    public static CompletableFuture<String> sendYesterday(String groupOpenId, RT replyTarget) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            throw new IllegalArgumentException("群 OpenID 不能为空");
        }
        return Holder.WORKER.submit(groupOpenId.trim(), replyTarget);
    }

    private static String send(Job job, RT replyTarget) throws Exception {
        Report report;
        try (var connection = DatabaseManager.getConnection()) {
            report = GroupDailyReportRepository.query(connection, job.groupOpenId(), job.date());
        }
        if (report.entries().isEmpty()) return null;
        Markdown markdown = new Markdown(render(report));
        String messageId = replyTarget == null
                ? GroupChat.sendMessage(job.groupOpenId(), markdown, null, null)
                : GroupChat.replyMessage(job.groupOpenId(), replyTarget, markdown, null, null);
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalStateException("群发言排行发送失败");
        }
        return messageId;
    }

    static String render(Report report) {
        StringBuilder text = new StringBuilder("##### 昨日群发言排行\n\n");
        text.append("> ").append(report.date()).append(" · 共 ").append(report.messages())
                .append(" 条 · ").append(report.members()).append(" 人参与\n\n");
        for (var entry : report.entries()) {
            String icon = switch (entry.position()) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "▫️ " + entry.position() + ".";
            };
            text.append(icon).append(' ').append(Markdown.at(entry.userOpenId()))
                    .append(" · ").append(entry.messages()).append(" 条\n\n");
        }
        return text.toString().stripTrailing();
    }

    record Job(String groupOpenId, LocalDate date) {
    }

    private record Completed(String messageId) {
    }

    @FunctionalInterface
    interface Operation {
        String run(Job job, RT replyTarget) throws Exception;
    }

    static final class Worker implements AutoCloseable {
        private final ThreadPoolExecutor executor;
        private final Map<Job, CompletableFuture<String>> pending = new HashMap<>();
        private final Cache<Job, Completed> completed = CacheBuilder.newBuilder()
                .maximumSize(4096).expireAfterWrite(36, TimeUnit.HOURS).build();
        private final Clock clock;
        private final Operation operation;
        private final long intervalNanos;
        private long nextStartNanos = System.nanoTime();

        Worker(int capacity, long intervalMillis, Clock clock, Operation operation) {
            this.clock = clock;
            this.operation = operation;
            intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
            executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(capacity),
                    Thread.ofPlatform().daemon(true).name("group-daily-report").factory(),
                    new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
        }

        synchronized CompletableFuture<String> submit(String groupOpenId, RT replyTarget) {
            Job job = new Job(groupOpenId, LocalDate.now(clock.withZone(GroupDailyReportRepository.ZONE)).minusDays(1));
            Completed previous = completed.getIfPresent(job);
            if (previous != null) return CompletableFuture.completedFuture(previous.messageId());
            CompletableFuture<String> existing = pending.get(job);
            if (existing != null) return existing.copy();
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.put(job, future);
            try {
                executor.execute(() -> run(job, replyTarget, future));
            } catch (RejectedExecutionException e) {
                pending.remove(job);
                future.completeExceptionally(e);
            }
            return future.copy();
        }

        private void run(Job job, RT replyTarget, CompletableFuture<String> future) {
            try {
                long delay = nextStartNanos - System.nanoTime();
                if (delay > 0) TimeUnit.NANOSECONDS.sleep(delay);
                String messageId = operation.run(job, replyTarget);
                synchronized (this) {
                    completed.put(job, new Completed(messageId));
                    pending.remove(job);
                }
                future.complete(messageId);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                synchronized (this) {
                    pending.remove(job);
                }
                log.warn("群发言排行处理失败: groupOpenId={}, date={}", job.groupOpenId(), job.date(), e);
                future.completeExceptionally(e);
            } finally {
                nextStartNanos = System.nanoTime() + intervalNanos;
            }
        }

        @Override
        public void close() {
            executor.shutdown();
        }
    }
}
