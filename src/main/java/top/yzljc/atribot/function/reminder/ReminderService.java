package top.yzljc.atribot.function.reminder;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.service.Scheduler;
import top.yzljc.atribot.service.textreview.TextReviewService;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName ReminderService
 * @Created_at 2026/09/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.reminder
 */
@Slf4j
public final class ReminderService {
    public static final String USE_PERMISSION = "test.reminder.use";
    public static final String UNLIMIT_PERMISSION = "test.reminder.unlimit";
    public static final String BETA_ONLY = "该功能内测中，敬请期待！";
    public static final String ERROR = "暂时无法设置提醒，请稍后再试。";
    public static final String BUSY = "设置请求正在处理中，请稍后再试。";
    public static final String NEED_TIME = "请提供明确的提醒时间及重复周期。";
    public static final String REJECTED = "该请求无法设置提醒，请调整后重试。";
    public static final String TOO_LONG = "输入过长，请缩短至200个字符以内后重试。";
    public static final String FULL = "任务数量已达上限，请在提醒列表中删除不再需要的任务。";
    public static final String LIMITED = "设置操作过于频繁，请30分钟后再试。";
    public static final String NOT_FOUND = "未找到该任务，请使用 提醒列表 查看任务ID。";
    public static final String USAGE = "发送“提醒 <时间或周期>”，确认时间后再发送事项内容；发送“提醒列表”管理任务。";
    public static final String CUSTOM_EXPIRED = "本次内容填写已超时或提醒时间已过，请重新发送 提醒 <时间>。";
    private static final Duration INPUT_TIMEOUT = Duration.ofMinutes(2);
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ReminderSchedule.ZONE);
    private static final Pattern INJECTION = Pattern.compile(
            "忽略.{0,12}(指令|规则|提示词)|系统提示词|开发者指令|越狱|"
                    + "ignore.{0,20}(instruction|rule|prompt)|systemprompt|developer(message|instruction)|"
                    + "<\\|[^>]{0,40}\\|>|\\[inst\\]|\\[cq:|<qqbot-|<script|"
                    + "(role|角色)[\"':：=]*(system|assistant|developer)");
    private final ReminderStore store;
    private final ReminderParser parser;
    private final Review review;
    private final Permissions permissions;
    private final Clock clock;
    private final Semaphore parsing = new Semaphore(4);
    private final Semaphore pendingSlots = new Semaphore(1024);
    private final ConcurrentHashMap<String, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Cache<InputKey, Boolean> consumedInputs = CacheBuilder.newBuilder()
            .maximumSize(4096).expireAfterWrite(10, TimeUnit.MINUTES).build();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean ready;

    @FunctionalInterface
    public interface Review { boolean allowed(String input) throws Exception; }

    @FunctionalInterface
    public interface Permissions { boolean has(String userId, String permission); }

    private static final class Holder {
        private static final ReminderService INSTANCE = new ReminderService(new ReminderRepository(),
                new ReminderAiParser(), input -> TextReviewService.reviewDetailed(input).replacements().isEmpty(),
                Clock.systemUTC(), (userId, permission) -> !OfficialUsers.isBlocked(userId)
                        && !OfficialUsers.isIgnored(userId) && (OfficialUsers.isAdmin(userId)
                        || OfficialUsers.hasPermission(userId, permission)));
    }

    public static ReminderService getInstance() { return Holder.INSTANCE; }

    ReminderService(ReminderStore store, ReminderParser parser, Review review, Clock clock) {
        this(store, parser, review, clock, (userId, permission) -> USE_PERMISSION.equals(permission));
    }

    public ReminderService(ReminderStore store, ReminderParser parser, Review review, Clock clock,
                           Permissions permissions) {
        this.store = store;
        this.parser = parser;
        this.review = review;
        this.clock = clock;
        this.permissions = permissions;
    }

    public void init() throws Exception {
        store.init();
        ready = true;
    }

    public void start(Scheduler scheduler) {
        if (ready && started.compareAndSet(false, true)) {
            scheduler.runTaskTimerAsynchronously(this::scan, 5_000, 5_000);
        }
    }

    /**
     * 解析提醒时间并等待下一条事项消息，此时尚未启用任务
     *
     * @param userId 任务所属用户
     * @param requestId 本次设置消息 ID
     * @param input 时间或周期描述
     * @return 时间确认提示或失败原因
     */
    public String prepare(String userId, String requestId, String input) {
        if (!validIdentity(userId, 128) || !validIdentity(requestId, 128)) return ERROR;
        if (!hasPermission(userId, USE_PERMISSION)) return BETA_ONLY;
        if (!ready) return ERROR;
        if (input == null || input.isBlank()) return NEED_TIME;
        if (input.codePointCount(0, input.length()) > 200) return TOO_LONG;
        if (!parsing.tryAcquire()) return BUSY;
        boolean admitted = false;
        boolean waiting = false;
        PendingInput pending = null;
        String stage = "admission";
        try {
            Instant receivedAt = clock.instant();
            if (!pendingSlots.tryAcquire()) return BUSY;
            PendingInput candidate = new PendingInput(requestId, receivedAt.plus(INPUT_TIMEOUT));
            PendingInput previous = pendingInputs.putIfAbsent(userId, candidate);
            if (previous != null) {
                pendingSlots.release();
                return "已有提醒正在设置，请先发送事项内容或发送“取消”。";
            }
            pending = candidate;
            ReminderStore.Result begin = store.begin(userId, requestId, null, receivedAt,
                    hasPermission(userId, UNLIMIT_PERMISSION));
            if (begin.code() != ReminderStore.Code.READY) return reply(begin);
            admitted = true;
            stage = "review";
            if (looksInjected(input) || !review.allowed(input)) {
                store.reject(userId, requestId, ReminderStore.Code.REJECTED);
                return REJECTED;
            }
            stage = "time_parse";
            ReminderParser.Result result = parser.parse(input, receivedAt);
            if (result.status() != ReminderParser.Status.OK) {
                ReminderStore.Code code = ReminderStore.Code.valueOf(result.status().name());
                store.reject(userId, requestId, code);
                return reply(new ReminderStore.Result(code, 0));
            }
            if (!hasPermission(userId, USE_PERMISSION)) {
                store.reject(userId, requestId, ReminderStore.Code.ERROR);
                return BETA_ONLY;
            }
            stage = "schedule";
            Instant firstRun = result.schedule().nextAfter(receivedAt, null);
            Instant now = clock.instant();
            if (!now.isBefore(pending.expiresAt) || firstRun == null || !firstRun.isAfter(now)
                    || firstRun.isAfter(now.plus(Duration.ofDays(366)))) {
                store.reject(userId, requestId, ReminderStore.Code.AMBIGUOUS);
                return NEED_TIME;
            }
            synchronized (pending) {
                pending.schedule = result.schedule();
                pending.firstRun = firstRun;
            }
            waiting = true;
            return "提醒时间：" + DISPLAY_TIME.format(firstRun) + "（北京时间" + recurrence(result.schedule())
                    + "）。请发送需要提醒的文字内容，200字以内。\n本次设置请求两分钟内有效，发送“取消”可退出。";

        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("提醒设置失败: stage={}, errorType={}, source={}", stage, e.getClass().getSimpleName(),
                    e.getStackTrace().length == 0 ? "unknown" : e.getStackTrace()[0]);
            if (admitted) {
                try { store.reject(userId, requestId, ReminderStore.Code.ERROR); }
                catch (Exception failure) { log.warn("提醒请求结束失败: errorType={}", failure.getClass().getSimpleName()); }
            }
            return ERROR;
        } finally {
            if (pending != null && !waiting) removePending(userId, pending);
            parsing.release();
        }
    }

    public boolean hasPendingCustom(String userId) {
        return userId != null && pendingInputs.containsKey(userId);
    }

    public boolean isConsumedCustomInput(String userId, String messageId) {
        return consumedInputs.getIfPresent(new InputKey(userId, messageId)) != null;
    }

    /**
     * 在事件接收线程预占下一条内容，防止并发消息替换首条输入
     *
     * @param userId 用户 OpenID
     * @param messageId 内容消息 ID
     * @return 预占成功时返回 true；重复消息、尚未确认时间或已在处理时返回 false
     */
    public boolean claimCustomInput(String userId, String messageId) {
        if (!validIdentity(messageId, 128)) return false;
        PendingInput pending = pendingInputs.get(userId);
        if (pending == null) return false;
        synchronized (pending) {
            if (pendingInputs.get(userId) != pending || pending.schedule == null || pending.claimedMessageId != null
                    || pending.requestId.equals(messageId)) return false;
            if (consumedInputs.asMap().putIfAbsent(new InputKey(userId, messageId), true) != null) return false;
            pending.claimedMessageId = messageId;
            return true;
        }
    }

    /**
     * 工作队列拒绝任务时释放预占，允许重新提交内容消息
     *
     * @param userId 用户 OpenID
     * @param messageId 已预占的内容消息 ID
     */
    public void releaseCustomInput(String userId, String messageId) {
        PendingInput pending = pendingInputs.get(userId);
        if (pending == null) return;
        synchronized (pending) {
            if (messageId.equals(pending.claimedMessageId)) {
                pending.claimedMessageId = null;
                consumedInputs.invalidate(new InputKey(userId, messageId));
            }
        }
    }

    /**
     * 审查内容并使用第二条消息的引用索引完成提醒设置
     *
     * @param userId 任务所属用户
     * @param messageId 已预占的内容消息 ID
     * @param refIdx 内容消息的引用索引
     * @param input 事项文字，不再参与时间解析
     * @return 设置结果；预占已失效时返回 null
     */
    public String completeCustom(String userId, String messageId, String refIdx, String input) {
        PendingInput pending = pendingInputs.get(userId);
        if (pending == null) return null;
        synchronized (pending) {
            if (!messageId.equals(pending.claimedMessageId) || pending.processing) return null;
            pending.processing = true;
        }
        boolean keepWaiting = false;
        boolean acquired = false;
        try {
            if (!clock.instant().isBefore(pending.expiresAt) || !pending.firstRun.isAfter(clock.instant())) {
                rejectPending(userId, pending);
                return CUSTOM_EXPIRED;
            }
            if (!hasPermission(userId, USE_PERMISSION)) {
                rejectPending(userId, pending);
                return BETA_ONLY;
            }
            if (input == null || input.isBlank() || input.codePointCount(0, input.length()) > 200) {
                keepWaiting = true;
                return "请发送200字以内的文字事项，或发送“取消”退出。";
            }
            if (!validIdentity(refIdx, 512)) {
                keepWaiting = true;
                return "当前消息无法引用，请重新发送事项内容，或发送“取消”退出。";
            }
            acquired = parsing.tryAcquire();
            if (!acquired) {
                keepWaiting = true;
                return BUSY;
            }
            if (looksInjected(input) || !review.allowed(input)) {
                keepWaiting = true;
                return "该内容无法用于提醒，请重新发送事项内容，或发送“取消”退出。";
            }
            if (!hasPermission(userId, USE_PERMISSION)) {
                rejectPending(userId, pending);
                return BETA_ONLY;
            }
            Instant now = clock.instant();
            if (!now.isBefore(pending.expiresAt) || !pending.firstRun.isAfter(now)) {
                rejectPending(userId, pending);
                return CUSTOM_EXPIRED;
            }
            return reply(store.save(userId, pending.requestId, refIdx, pending.schedule, now,
                    hasPermission(userId, UNLIMIT_PERMISSION), pending.firstRun));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("提醒内容保存失败: errorType={}", e.getClass().getSimpleName());
            rejectPending(userId, pending);
            return ERROR;
        } finally {
            if (acquired) parsing.release();
            synchronized (pending) {
                if (keepWaiting) {
                    pending.claimedMessageId = null;
                    pending.processing = false;
                } else removePending(userId, pending);
            }
        }
    }

    public String cancelCustom(String userId) {
        PendingInput pending = pendingInputs.get(userId);
        if (pending == null) return "当前没有等待填写的提醒。";
        synchronized (pending) {
            if (pending.schedule == null || pending.claimedMessageId != null) return BUSY;
            rejectPending(userId, pending);
            removePending(userId, pending);
        }
        return "已取消本次提醒设置。";
    }

    /**
     * 时间确认消息发送失败时，按原始请求 ID 清理待输入状态
     *
     * @param userId 用户 OpenID
     * @param requestId 原始设置消息 ID
     */
    public void discardCustom(String userId, String requestId) {
        PendingInput pending = pendingInputs.get(userId);
        if (pending == null) return;
        synchronized (pending) {
            if (!pending.requestId.equals(requestId) || pending.claimedMessageId != null) return;
            rejectPending(userId, pending);
            removePending(userId, pending);
        }
    }

    void expireCustomInputs() {
        Instant now = clock.instant();
        pendingInputs.forEach((userId, pending) -> {
            synchronized (pending) {
                if (pending.schedule != null && pending.claimedMessageId == null && !now.isBefore(pending.expiresAt)) {
                    rejectPending(userId, pending);
                    removePending(userId, pending);
                }
            }
        });
        consumedInputs.cleanUp();
    }

    private void rejectPending(String userId, PendingInput pending) {
        try { store.reject(userId, pending.requestId, ReminderStore.Code.ERROR); }
        catch (Exception e) { log.warn("提醒请求结束失败: errorType={}", e.getClass().getSimpleName()); }
    }

    private void removePending(String userId, PendingInput pending) {
        if (pendingInputs.remove(userId, pending)) pendingSlots.release();
    }

    private static String recurrence(ReminderSchedule schedule) {
        return switch (schedule.kind()) {
            case ONCE -> "";
            case DAILY -> "，每天重复";
            case WEEKLY -> "，每周重复";
            case MONTHLY -> "，每月重复";
            case INTERVAL -> "，每" + schedule.intervalMinutes() + "分钟重复";
        };
    }

    private record InputKey(String userId, String messageId) {}

    private static final class PendingInput {
        private final String requestId;
        private final Instant expiresAt;
        private ReminderSchedule schedule;
        private Instant firstRun;
        private String claimedMessageId;
        private boolean processing;

        private PendingInput(String requestId, Instant expiresAt) {
            this.requestId = requestId;
            this.expiresAt = expiresAt;
        }
    }

    public String list(String userId) {
        return list(userId, 1);
    }

    public String list(String userId, int page) {
        if (!validIdentity(userId, 128)) return ERROR;
        if (!hasPermission(userId, USE_PERMISSION)) return BETA_ONLY;
        if (page < 1 || page > 1_000_000) return USAGE;
        if (!ready) return ERROR;
        try {
            List<ReminderStore.Task> tasks = store.listTasks(userId, page);
            if (tasks.isEmpty()) return page == 1 ? "暂无提醒任务，发送“提醒 <时间>”创建。" : "暂无此页任务。";
            StringBuilder text = new StringBuilder("**提醒列表**\n\n");
            for (int i = 0; i < Math.min(tasks.size(), 10); i++) {
                ReminderStore.Task task = tasks.get(i);
                text.append("**任务ID #").append(task.id()).append("**  ")
                        .append(Markdown.enterCommand("/提醒列表 开启 " + task.id(), "开启")).append(' ')
                        .append(Markdown.enterCommand("/提醒列表 关闭 " + task.id(), "关闭")).append(' ')
                        .append(Markdown.enterCommand("/提醒列表 删除 " + task.id(), "删除"))
                        .append("\n> ").append(task.enabled() ? "已开启 · 下次提醒：" : "已关闭 · 原定时间：")
                        .append(DISPLAY_TIME.format(task.nextRun())).append("\n\n");
            }
            if (page > 1) text.append(Markdown.enterCommand("/提醒列表 " + (page - 1), "上一页")).append(' ');
            if (tasks.size() > 10) text.append(Markdown.enterCommand("/提醒列表 " + (page + 1), "下一页"));
            return text.toString();
        } catch (Exception e) {
            log.warn("读取提醒任务失败: errorType={}", e.getClass().getSimpleName());
            return ERROR;
        }
    }

    public String setEnabled(String userId, long taskId, boolean enabled) {
        if (!validIdentity(userId, 128) || taskId < 1) return ERROR;
        if (!hasPermission(userId, USE_PERMISSION)) return BETA_ONLY;
        if (!ready) return ERROR;
        try {
            if (enabled) {
                ReminderStore.Result result = store.enable(userId, taskId, clock.instant());
                if (result.code() == ReminderStore.Code.EXPIRED) return "该一次性提醒的时间已过，请重新发送“提醒 <时间>”设置。";
                if (result.code() != ReminderStore.Code.SAVED) return reply(result);
            } else if (!store.stop(userId, taskId, false)) return NOT_FOUND;
            return (enabled ? "已开启任务ID #" : "已关闭任务ID #") + taskId + "。";
        } catch (Exception e) {
            log.warn("提醒开关设置失败: errorType={}", e.getClass().getSimpleName());
            return ERROR;
        }
    }

    public String delete(String userId, long taskId) {
        if (!validIdentity(userId, 128) || taskId < 1) return ERROR;
        if (!hasPermission(userId, USE_PERMISSION)) return BETA_ONLY;
        if (!ready) return ERROR;
        try {
            if (!store.stop(userId, taskId, true)) return NOT_FOUND;
            return "已删除任务ID #" + taskId + "。";
        } catch (Exception e) {
            log.warn("删除提醒任务失败: errorType={}", e.getClass().getSimpleName());
            return ERROR;
        }
    }

    private void scan() {
        try {
            expireCustomInputs();
            store.dispatchDue(clock.instant(), (userId, refIdx, text) ->
                    hasPermission(userId, USE_PERMISSION)
                            && !OfficialUsers.isBlocked(userId) && !OfficialUsers.isIgnored(userId)
                            && C2CChat.refMessage(userId, refIdx, text) != null);
        } catch (Exception e) {
            log.warn("扫描到期提醒失败: errorType={}", e.getClass().getSimpleName());
        }
    }

    public static String reminderText(long taskId) {
        if (taskId < 1) throw new IllegalArgumentException("Invalid reminder ID");
        return "任务提醒！请留意您先前设置的事项。\n事项ID #" + taskId + "，如需关闭请使用 提醒列表。";
    }

    public static String reminderText(long taskId, Instant now) {
        if (taskId < 1) throw new IllegalArgumentException("Invalid reminder ID");
        return "现在是" + DISPLAY_TIME.format(now) + "，你有一个提醒事项！\n事项ID #" + taskId
                + "，如需关闭请使用 提醒列表。";
    }

    public static boolean looksInjected(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[\\p{Cf}\\s]", "");
        return INJECTION.matcher(normalized).find();
    }

    private boolean hasPermission(String userId, String permission) {
        try {
            return permissions.has(userId, permission);
        } catch (RuntimeException e) {
            log.warn("提醒权限查询失败: errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean validIdentity(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit;
    }

    private static String reply(ReminderStore.Result result) {
        return switch (result.code()) {
            case SAVED -> "已设置事项ID #" + result.taskId() + "。";
            case LIMITED -> LIMITED;
            case FULL -> FULL;
            case BUSY -> BUSY;
            case NOT_FOUND -> NOT_FOUND;
            case CONFLICT -> "任务已发生变更，请重新设置。";
            case NEED_TIME, AMBIGUOUS -> NEED_TIME;
            case REJECTED -> REJECTED;
            default -> ERROR;
        };
    }
}
