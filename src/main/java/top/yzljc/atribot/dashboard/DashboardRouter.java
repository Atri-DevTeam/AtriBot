package top.yzljc.atribot.dashboard;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.miniapp.MiniappSessions;
import top.yzljc.atribot.miniapp.service.MiniappProfileService;
import top.yzljc.atribot.webui.WebUISessionManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Dashboard 的 QQ 互联授权、绑定与页面会话入口
 *
 * @Author YZ_Ljc_
 * @ClassName DashboardRouter
 * @Created_at 2026/10/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.dashboard
 */
public final class DashboardRouter {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardRouter.class);
    private static final String STATE_COOKIE = "dashboard_qq_state";
    private static final String SESSION_COOKIE = DashboardSessions.SESSION_COOKIE;
    private DashboardRouter() {}

    record StartRequest(String userOpenId, String ticket, String nav, String wwv, boolean mobile, boolean qq) {}
    @FunctionalInterface
    interface IdentityExchange { QqConnectClient.Profile profile(DashboardSettings settings, String code) throws IOException, InterruptedException; }

    interface AccountBindings {
        MiniappSessions.Identity find(DashboardSettings settings, String openId) throws SQLException;
        boolean bind(DashboardSettings settings, String openId, MiniappSessions.Identity identity) throws SQLException;
    }

    public static void register(Javalin server, DashboardSessions sessions, MiniappSessions pages) {
        AccountBindings bindings = new AccountBindings() {
            @Override public MiniappSessions.Identity find(DashboardSettings settings, String openId) throws SQLException {
                var account = UnifiedAuthentication.findByQqConnect(openId);
                return account == null || account.qqUserOpenId() == null || account.qqUserOpenId().isBlank() ? null
                        : new MiniappSessions.Identity(account.qqUserOpenId(), account.username() == null ? "" : account.username());
            }

            @Override public boolean bind(DashboardSettings settings, String openId, MiniappSessions.Identity identity) throws SQLException {
                return UnifiedAuthentication.bindQqConnect(identity.userId(), identity.displayName(), openId);
            }
        };
        register(server, sessions, pages, bindings, DashboardSettings::current, new QqConnectClient()::profile,
                userId -> !OfficialUsers.isBlocked(userId) && !OfficialUsers.isIgnored(userId));
    }

    static void register(Javalin server, DashboardSessions sessions, MiniappSessions pages,
                         AccountBindings bindings, Supplier<DashboardSettings> configuration,
                         IdentityExchange exchange, Predicate<String> allowed) {
        server.get("/", ctx -> ctx.header("Cache-Control", "no-store")
                .redirect("/dashboard/" + ("1".equals(singleQuery(ctx, "qq")) ? "?qq=1" : "")));
        server.before("/dashboard", ctx -> guard(ctx, configuration.get()));
        server.before("/dashboard/*", ctx -> guard(ctx, configuration.get()));
        server.before("/auth/qq/callback", ctx -> guard(ctx, configuration.get()));
        server.get("/dashboard", DashboardRouter::index);
        server.get("/dashboard/", DashboardRouter::index);
        server.get("/dashboard/api/bot", ctx -> ctx.json(MiniappProfileService.currentBot()));

        server.post("/dashboard/api/auth/start", ctx -> {
            DashboardSettings settings = configuration.get();
            sameOrigin(ctx, settings);
            StartRequest request = ctx.bodyAsClass(StartRequest.class);
            if (request == null || request.userOpenId() != null && request.userOpenId().length() > 128
                    || request.ticket() != null && request.ticket().length() > 128) throw new BadRequestResponse();
            String navigation = navigation(request.nav(), request.wwv(), request.qq());
            try {
                String state = sessions.begin(request.userOpenId(), request.ticket(), settings, navigation);
                cookie(ctx, STATE_COOKIE, state, "/", DashboardSessions.STATE_SECONDS, settings);
                ctx.json(Map.of("url", QqConnectClient.authorizeUrl(settings, state, request.mobile())));
            } catch (IllegalArgumentException expired) {
                ctx.status(400).json(Map.of("error", "BINDING_EXPIRED"));
            } catch (IllegalStateException busy) {
                ctx.status(429).json(Map.of("error", "LOGIN_BUSY"));
            }
        });

        Handler completeLogin = ctx -> {
            DashboardSettings settings = configuration.get();
            String state = singleQuery(ctx, "state");
            var pending = sessions.consume(state, ctx.cookie(STATE_COOKIE));
            if (pending == null || !pending.appId().equals(settings.appId()) || !pending.botAppId().equals(settings.botAppId())) {
                ctx.redirect("/dashboard/?auth_error=LOGIN_EXPIRED");
                return;
            }
            cookie(ctx, STATE_COOKIE, "", "/", 0, settings);
            String code = singleQuery(ctx, "code");
            String error = null;
            try {
                if (code == null || code.isBlank() || code.length() > 512 || ctx.queryParam("error") != null) {
                    error = "LOGIN_CANCELLED";
                } else {
                    var profile = exchange.profile(settings, code);
                    String qqOpenId = profile.openId();
                    if (pending.binding() != null && !allowed.test(pending.binding().userId())) error = "ACCOUNT_RESTRICTED";
                    else if (pending.binding() != null && !bindings.bind(settings, qqOpenId, pending.binding())) error = "BINDING_CONFLICT";
                    else {
                        var identity = bindings.find(settings, qqOpenId);
                        if (identity != null && !allowed.test(identity.userId())) error = "ACCOUNT_RESTRICTED";
                        else {
                            sessions.revoke(ctx.cookie(SESSION_COOKIE));
                            String token = sessions.login(identity, qqOpenId, profile.avatarUrl(), settings);
                            cookie(ctx, SESSION_COOKIE, token, "/", DashboardSessions.LOGIN_SECONDS, settings);
                        }
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                error = "LOGIN_FAILED";
            } catch (Exception failure) {
                LOG.warn("Dashboard QQ 登录失败: errorType={}", failure.getClass().getSimpleName());
                error = "LOGIN_FAILED";
            }
            String query = pending.navigation();
            if (error != null) query += (query.isEmpty() ? "" : "&") + "auth_error=" + error;
            ctx.redirect("/dashboard/" + (query.isEmpty() ? "" : "?" + query));
        };
        server.get("/dashboard/auth/complete", completeLogin);
        server.get("/auth/qq/callback", ctx -> {
            DashboardSettings settings = configuration.get();
            if (!settings.separateCallbackOrigin()) {
                completeLogin.handle(ctx);
                return;
            }
            // 回到页面域名后校验该浏览器的 state Cookie，登录 Cookie 也只在页面域名签发
            String state = singleQuery(ctx, "state");
            if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) {
                ctx.redirect(settings.entryUrl() + "?auth_error=LOGIN_EXPIRED");
                return;
            }
            String code = singleQuery(ctx, "code");
            String target = settings.origin() + "/dashboard/auth/complete?state=" + QqConnectClient.encode(state);
            if (code != null && !code.isBlank() && code.length() <= 512 && ctx.queryParam("error") == null)
                target += "&code=" + QqConnectClient.encode(code);
            else target += "&error=LOGIN_CANCELLED";
            ctx.redirect(target);
        });

        server.post("/dashboard/api/auth/session", ctx -> {
            DashboardSettings settings = configuration.get();
            sameOrigin(ctx, settings);
            String token = ctx.cookie(SESSION_COOKIE);
            var login = sessions.authenticate(token, settings);
            if (login == null) { ctx.json(Map.of("status", "anonymous")); return; }
            if (login.identity() == null) { ctx.json(Map.of("status", "unbound")); return; }
            if (!allowed.test(login.identity().userId())) {
                sessions.revoke(token);
                ctx.status(403).json(Map.of("error", "ACCOUNT_RESTRICTED"));
                return;
            }
            var access = pages.createAccess(login.identity(), () -> sessions.authenticate(token, configuration.get()) != null
                    && allowed.test(login.identity().userId()));
            if (access == null) ctx.status(429).json(Map.of("error", "LOGIN_BUSY"));
            else ctx.json(Map.of("status", "ready", "access", access,
                    "canManage", WebUISessionManager.isAdministrator(UnifiedAuthentication.getCachedQqUserAccount(login.identity().userId())),
                    "webuiEnabled", WebUISessionManager.isActive()));
        });

        server.post("/dashboard/api/auth/logout", ctx -> {
            DashboardSettings settings = configuration.get();
            sameOrigin(ctx, settings);
            sessions.revoke(ctx.cookie(SESSION_COOKIE));
            cookie(ctx, SESSION_COOKIE, "", "/", 0, settings);
            ctx.status(204);
        });
    }

    private static void sameOrigin(Context ctx, DashboardSettings settings) {
        if (!Objects.equals(settings.origin(), ctx.header("Origin"))) throw new ForbiddenResponse();
        if (ctx.contentType() == null || !ctx.contentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json")
                || ctx.bodyAsBytes().length > 4096) throw new BadRequestResponse();
    }

    private static void guard(Context ctx, DashboardSettings settings) {
        ctx.header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff").header("X-Frame-Options", "DENY");
        if (!settings.available()) {
            ctx.status(503).json(Map.of("error", "DASHBOARD_DISABLED"));
            ctx.skipRemainingHandlers();
        }
    }

    private static void cookie(Context ctx, String name, String value, String path, int seconds, DashboardSettings settings) {
        ctx.res().addHeader("Set-Cookie", name + "=" + value + "; Path=" + path + "; Max-Age=" + seconds
                + "; HttpOnly; SameSite=Lax" + (settings.secure() ? "; Secure" : ""));
    }

    private static String singleQuery(Context ctx, String name) {
        var values = ctx.queryParams(name);
        return values.size() == 1 ? values.getFirst() : null;
    }

    private static String navigation(String nav, String wwv, boolean qq) {
        String query = "";
        if (nav != null && nav.matches("[A-Za-z0-9_.-]{1,32}")) query = "_nav=" + nav;
        if (wwv != null && wwv.matches("[0-9]{1,10}")) query += (query.isEmpty() ? "" : "&") + "_wwv=" + wwv;
        if (qq) query += (query.isEmpty() ? "" : "&") + "qq=1";
        return query;
    }

    private static void index(Context ctx) {
        var stream = DashboardRouter.class.getResourceAsStream("/miniapp/index.html");
        if (stream == null) ctx.status(503).result("Miniapp assets have not been built.");
        else ctx.contentType("text/html; charset=utf-8").result(stream);
    }
}
