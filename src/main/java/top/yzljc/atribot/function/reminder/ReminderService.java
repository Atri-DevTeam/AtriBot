package top.yzljc.atribot.function.reminder;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.service.Scheduler;
import top.yzljc.atribot.service.textreview.TextReviewService;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Semaphore;
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
    public static final String FULL = "任务数量已达上限，请先删除已有任务。";
    public static final String LIMITED = "设置操作过于频繁，请30分钟后再试。";
    public static final String NOT_FOUND = "未找到该任务，请使用 /提醒 设置 查看任务ID。";
    public static final String USAGE = "私聊使用：/提醒 添加 <时间和事项>、/提醒 设置、/提醒 修改 <任务ID> <时间和事项>、/提醒 关闭 <任务ID>、/提醒 删除 <任务ID>。";
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
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean ready;

    @FunctionalInterface
    public interface Review { boolean allowed(String input) throws Exception; }

    @FunctionalInterface
    public interface Permissions { boolean has(String userId, String permission); }

    private static final class Holder {
        private static final ReminderService INSTANCE = new ReminderService(new ReminderRepository(),
                new ReminderAiParser(), input -> TextReviewService.reviewDetailed(input).replacements().isEmpty(),
                Clock.systemUTC(), (userId, permission) -> OfficialUsers.isAdmin(userId)
                        || OfficialUsers.hasPermission(userId, permission));
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

    public String set(String userId, String requestId, String refIdx, Long taskId, String input) {
        if (!validIdentity(userId, 128) || !validIdentity(requestId, 128)) return ERROR;
        if (!hasPermission(userId, USE_PERMISSION)) return BETA_ONLY;
        if (!ready) return ERROR;
        if (input == null || input.isBlank()) return NEED_TIME;
        if (input.codePointCount(0, input.length()) > 200) return TOO_LONG;
        if (!parsing.tryAcquire()) return BUSY;
        boolean admitted = false;
        try {
            Instant receivedAt = clock.instant();
            ReminderStore.Result begin = store.begin(userId, requestId, taskId, receivedAt,
                    hasPermission(userId, UNLIMIT_PERMISSION));
            if (begin.code() != ReminderStore.Code.READY) return reply(begin);
            admitted = true;
            if (!validIdentity(refIdx, 512)) {
                store.reject(userId, requestId, ReminderStore.Code.ERROR);
                return "当前消息无法关联提醒，请重新发送设置请求。";
            }
            if (looksInjected(input) || !review.allowed(input)) {
                store.reject(userId, requestId, ReminderStore.Code.REJECTED);
                return REJECTED;
            }
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
            return reply(store.save(userId, requestId, refIdx, result.schedule(), clock.instant(),
                    hasPermission(userId, UNLIMIT_PERMISSION)));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("提醒设置失败: errorType={}", e.getClass().getSimpleName());
            if (admitted) {
                try { store.reject(userId, requestId, ReminderStore.Code.ERROR); }
                catch (Exception failure) { log.warn("提醒请求结束失败: errorType={}", failure.getClass().getSimpleName()); }
            }
            return ERROR;
        } finally {
            parsing.release();
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
            List<Long> ids = store.list(userId, page);
            if (ids.isEmpty()) return page == 1 ? "暂无提醒任务。" : "暂无此页任务。";
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < Math.min(ids.size(), 10); i++) {
                text.append("事项ID #").append(ids.get(i)).append('\n');
            }
            if (ids.size() > 10) text.append("下一页：/提醒 设置 ").append(page + 1).append('\n');
            return text.append("关闭：/提醒 关闭 <任务ID>\n修改：/提醒 修改 <任务ID> <时间和事项>\n删除：/提醒 删除 <任务ID>").toString();
        } catch (Exception e) {
            log.warn("读取提醒任务失败: errorType={}", e.getClass().getSimpleName());
            return ERROR;
        }
    }

    public String stop(String userId, long taskId, boolean delete) {
        if (!validIdentity(userId, 128) || taskId < 1) return ERROR;
        if (!hasPermission(userId, USE_PERMISSION)) return BETA_ONLY;
        if (!ready) return ERROR;
        try {
            if (!store.stop(userId, taskId, delete)) return NOT_FOUND;
            return (delete ? "已删除事项ID #" : "已关闭事项ID #") + taskId + "。";
        } catch (Exception e) {
            log.warn("停止提醒任务失败: errorType={}", e.getClass().getSimpleName());
            return ERROR;
        }
    }

    private void scan() {
        try {
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
        return "任务提醒！请留意您先前设置的事项。\n事项ID #" + taskId + "，如需关闭请使用 /提醒 设置。";
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
