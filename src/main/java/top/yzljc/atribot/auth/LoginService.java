package top.yzljc.atribot.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 供插件调用的临时登录验证逻辑；请求接收、登录会话及对外响应由插件处理。
 *
 * @Author YZ_Ljc_
 * @ClassName LoginService
 * @Created_at 2026/09/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public final class LoginService implements AutoCloseable {
    public static final Duration REQUEST_TTL = Duration.ofMinutes(5);
    private static final int MAX_REQUESTS = 1024;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Object lock = new Object();
    private final Map<UUID, Pending> requests = new HashMap<>();
    // 已使用/取消的验证码也保留到原有效期结束，避免旧命令确认到新请求。
    private final Map<String, Pending> codes = new HashMap<>();
    private final ScheduledExecutorService cleaner;
    private boolean closed;

    private static final class Holder {
        private static final LoginService INSTANCE = new LoginService();
    }

    /** 插件应使用这个共享实例，或 PluginContext.getLoginService()。 */
    public static LoginService getInstance() { return Holder.INSTANCE; }

    private LoginService() { this(Clock.systemUTC(), true); }

    LoginService(Clock clock, boolean scheduleCleanup) {
        this.clock = Objects.requireNonNull(clock);
        cleaner = scheduleCleanup ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "api-login-expiry");
            thread.setDaemon(true);
            // 首次调用可能来自插件线程，不让共享清理线程持有插件 ClassLoader。
            thread.setContextClassLoader(LoginService.class.getClassLoader());
            return thread;
        }) : null;
        if (cleaner != null) cleaner.scheduleWithFixedDelay(this::cleanupExpired, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 创建验证申请，返回六位验证码和异步结果。路径由插件指定，例如 /api/*。
     * 到期以 TimeoutException 失败；取消或关闭服务以 CancellationException 失败。
     */
    public LoginRequest create(String... apiPaths) {
        return createWithPermission("api.login", apiPaths);
    }

    /** 由插件指定审批权限，不能由外部 HTTP 请求决定。 */
    public LoginRequest createWithPermission(String permission, String... apiPaths) {
        if (permission == null || !permission.matches("[a-zA-Z0-9_.-]{1,128}")) {
            throw new IllegalArgumentException("登录审批权限无效");
        }
        if (apiPaths == null || apiPaths.length == 0 || apiPaths.length > 64) {
            throw new IllegalArgumentException("必须指定 1-64 个 API 路径范围");
        }
        List<String> scopes = Arrays.stream(apiPaths).map(ApiPathScope::validate).distinct().toList();
        cleanupExpired();
        synchronized (lock) {
            if (closed) throw new IllegalStateException("登录服务已关闭");
            if (codes.size() >= MAX_REQUESTS) throw new IllegalStateException("登录申请过多，请稍后再试");
            String code;
            do { code = Integer.toString(100_000 + random.nextInt(900_000)); } while (codes.containsKey(code));
            UUID requestId = UUID.randomUUID();
            Pending pending = new Pending(requestId, clock.instant().plus(REQUEST_TTL), scopes, permission);
            requests.put(requestId, pending);
            codes.put(code, pending);
            return new LoginRequest(requestId, code, pending.expiresAt, scopes, pending.completion.minimalCompletionStage());
        }
    }

    /** 返回有效且尚未消费的验证码所要求的权限；无效时返回 null。 */
    public String requiredPermission(String code) {
        cleanupExpired();
        synchronized (lock) {
            Pending pending = codes.get(code);
            return !closed && pending != null && requests.containsKey(pending.requestId) ? pending.permission : null;
        }
    }

    /** 由 LoginCommand 在权限判断通过后调用；每个请求只能确认一次。 */
    public Approval approve(String code, LoginIdentity identity) {
        return approve(code, identity, null);
    }

    /** 命令端传入刚检查的权限，避免查询与确认之间验证码到期重用时批准另一种权限。 */
    public Approval approve(String code, LoginIdentity identity, String expectedPermission) {
        Objects.requireNonNull(identity);
        cleanupExpired();
        Pending pending;
        LoginResult result;
        synchronized (lock) {
            pending = codes.get(code);
            if (closed || pending == null || !clock.instant().isBefore(pending.expiresAt)
                    || (expectedPermission != null && !expectedPermission.equals(pending.permission))
                    || !requests.remove(pending.requestId, pending)) return Approval.INVALID_CODE;
            result = new LoginResult(identity, pending.apiPaths, clock.instant());
        }
        // 用户回调不得在内部锁中执行。
        pending.completion.complete(result);
        return Approval.APPROVED;
    }

    /** 取消尚未完成的申请。已确认的登录会话由插件自行管理。 */
    public boolean cancel(UUID requestId) {
        cleanupExpired();
        Pending pending;
        synchronized (lock) { pending = requests.remove(requestId); }
        if (pending == null) return false;
        pending.completion.completeExceptionally(new CancellationException("登录申请已取消"));
        return true;
    }

    void cleanupExpired() {
        List<Pending> expired = new ArrayList<>();
        synchronized (lock) {
            Instant now = clock.instant();
            codes.values().removeIf(pending -> {
                if (now.isBefore(pending.expiresAt)) return false;
                if (requests.remove(pending.requestId, pending)) expired.add(pending);
                return true;
            });
        }
        expired.forEach(pending -> pending.completion.completeExceptionally(new TimeoutException("验证码已过期")));
    }

    /** 仅供宿主关闭共享服务；插件停用时应 cancel 自己的未完成申请。 */
    @Override
    public void close() {
        List<Pending> pending;
        synchronized (lock) {
            closed = true;
            pending = new ArrayList<>(requests.values());
            requests.clear();
            codes.clear();
        }
        if (cleaner != null) cleaner.shutdownNow();
        pending.forEach(request -> request.completion.completeExceptionally(new CancellationException("登录服务已关闭")));
    }

    public enum Approval { APPROVED, INVALID_CODE }

    /** senderType 区分不同命令来源；userId 只在对应来源内有意义。 */
    public record LoginIdentity(String userId, String username, String senderType) {
        public LoginIdentity {
            if (userId == null || userId.isBlank() || senderType == null || senderType.isBlank()) {
                throw new IllegalArgumentException("登录者身份不能为空");
            }
        }
    }

    /** requestId 用于 Java 侧取消申请；不是对外访问凭证。 */
    public record LoginRequest(UUID requestId, String code, Instant expiresAt, List<String> apiPaths,
                               CompletionStage<LoginResult> completion) {
        public LoginRequest { apiPaths = List.copyOf(apiPaths); }
    }

    /** 验证结果，不是登录会话。插件自行决定会话有效期及如何响应原请求。 */
    public record LoginResult(LoginIdentity identity, List<String> apiPaths, Instant confirmedAt) {
        public LoginResult { apiPaths = List.copyOf(apiPaths); }

        /** 按申请时的范围匹配路径，不涉及 HTTP 或会话鉴权。 */
        public boolean allows(String path) {
            return apiPaths.stream().anyMatch(scope -> ApiPathScope.matches(scope, path));
        }
    }

    private static final class Pending {
        final String permission;
        final UUID requestId;
        final Instant expiresAt;
        final List<String> apiPaths;
        final CompletableFuture<LoginResult> completion = new CompletableFuture<>();

        Pending(UUID requestId, Instant expiresAt, List<String> apiPaths, String permission) {
            this.permission = permission;
            this.requestId = requestId;
            this.expiresAt = expiresAt;
            this.apiPaths = apiPaths;
        }
    }
}
