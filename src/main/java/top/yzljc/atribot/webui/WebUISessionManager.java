package top.yzljc.atribot.webui;

import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.dashboard.DashboardSessions;
import top.yzljc.atribot.dashboard.DashboardSettings;
import top.yzljc.atricore.auth.AccountStatus;
import top.yzljc.atricore.auth.AtriAccount;
import top.yzljc.atricore.auth.official.UnifiedRole;

import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * WebUI 服务开关及 QQ 互联管理员会话校验
 *
 * @Author YZ_Ljc_
 * @ClassName WebUISessionManager
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui
 */
@Slf4j
public final class WebUISessionManager {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static volatile DashboardSessions sessions;
    private static volatile Supplier<DashboardSettings> configuration = DashboardSettings::current;
    private static volatile BooleanSupplier developmentEnvironment = () -> false;
    private static final AtriAccount DEVELOPMENT_ACCOUNT = new AtriAccount(new UUID(0, 0), "本地调试", "local-dev",
            null, null, UnifiedRole.OWNER, List.of("*"), AccountStatus.ACTIVE, null, null);

    private WebUISessionManager() {
    }

    /**
     * @param logins   与个人面板共用的 QQ 互联会话，不在本类中关闭
     * @param settings 当前 QQ 互联配置，配置或绑定变化后旧会话不再有效
     */
    public static void configure(DashboardSessions logins, Supplier<DashboardSettings> settings) {
        configure(logins, settings, () -> false);
    }

    /**
     * @param logins 与个人面板共用的 QQ 互联会话
     * @param settings 当前 QQ 互联配置
     * @param development 运行服务的开发环境判断，仅开发环境允许 WebUI 免登录访问
     */
    public static void configure(DashboardSessions logins, Supplier<DashboardSettings> settings, BooleanSupplier development) {
        sessions = Objects.requireNonNull(logins);
        configuration = Objects.requireNonNull(settings);
        developmentEnvironment = Objects.requireNonNull(development);
    }

    public static synchronized void start() {
        GENERATION.incrementAndGet();
        SseBroadcaster.closeAll();
        ACTIVE.set(true);
        log.info("WebUI 服务已开启");
    }

    public static synchronized void stop() {
        ACTIVE.set(false);
        GENERATION.incrementAndGet();
        SseBroadcaster.closeAll();
        log.info("WebUI 服务已关闭，所有连接已断开");
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    /**
     * @param account 统一账号当前缓存记录，不接受权限节点替代管理员身份
     * @return 账号有效且权限组为 ADMIN 或 OWNER 时返回 true
     */
    public static boolean isAdministrator(AtriAccount account) {
        return account != null && account.status() == AccountStatus.ACTIVE
                && (account.role() == UnifiedRole.ADMIN || account.role() == UnifiedRole.OWNER);
    }

    public static AtriAccount currentAccount(String token) {
        if (developmentEnvironment.getAsBoolean()) return DEVELOPMENT_ACCOUNT;
        var login = currentLogin(token);
        return login == null || login.identity() == null ? null
                : UnifiedAuthentication.getCachedQqUserAccount(login.identity().userId());
    }

    private static DashboardSessions.Login currentLogin(String token) {
        DashboardSessions current = sessions;
        return current == null ? null : current.authenticate(token, configuration.get());
    }

    /**
     * 开发环境使用临时调试身份，其他环境检查 QQ 会话、账号状态、权限组及写请求 Origin
     *
     * @param ctx 当前 WebUI 请求
     */
    public static void require(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        if (!isActive()) {
            reject(ctx, ApiCode.SERVICE_UNAVAILABLE);
            return;
        }
        if (developmentEnvironment.getAsBoolean()) {
            ctx.attribute("webui.account", DEVELOPMENT_ACCOUNT);
            return;
        }
        var settings = configuration.get();
        String origin = ctx.header("Origin");
        boolean readOnly = Set.of("GET", "HEAD").contains(ctx.method().toString());
        if (((!readOnly || origin != null) && !Objects.equals(settings.origin(), origin))
                || "cross-site".equalsIgnoreCase(ctx.header("Sec-Fetch-Site"))) {
            reject(ctx, ApiCode.ACCESS_DENIED);
            return;
        }
        var login = currentLogin(ctx.cookie(DashboardSessions.SESSION_COOKIE));
        var account = login == null || login.identity() == null ? null
                : UnifiedAuthentication.getCachedQqUserAccount(login.identity().userId());
        if (account == null) {
            reject(ctx, ApiCode.AUTHENTICATION_REQUIRED);
            return;
        }
        if (!isAdministrator(account)) {
            reject(ctx, ApiCode.ACCESS_DENIED);
            return;
        }
        ctx.attribute("webui.account", account);
        ctx.attribute("webui.login", login);
    }

    /**
     * @param token 当前浏览器的 QQ 登录会话
     * @return 持续有效性检查；服务重开后旧检查永久失效，其他情况实时读取当前授权
     */
    public static BooleanSupplier authorization(String token) {
        long generation = GENERATION.get();
        return () -> isActive() && generation == GENERATION.get() && isAdministrator(currentAccount(token));
    }

    public static void logout(String token) {
        DashboardSessions current = sessions;
        if (current != null) current.revoke(token);
    }

    private static void reject(Context ctx, ApiCode code) {
        ctx.status(code.getHttpStatus()).json(Result.failure(code));
        ctx.skipRemainingHandlers();
    }
}
