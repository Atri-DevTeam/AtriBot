package top.yzljc.atribot.auth;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.dashboard.DashboardSessions;
import top.yzljc.atribot.dashboard.DashboardSettings;
import top.yzljc.atribot.dashboard.QqConnectClient;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.ApiCode;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 接收站点授权请求及服务端授权码兑换
 *
 * @Author YZ_Ljc_
 * @ClassName SiteAuthorizationRouter
 * @Created_at 2026/10/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public final class SiteAuthorizationRouter {
    private static final Logger LOG = LoggerFactory.getLogger(SiteAuthorizationRouter.class);

    private SiteAuthorizationRouter() {
    }

    /**
     * @param server        运行服务的 HTTP 实例
     * @param authorization 站点授权请求管理
     * @param sessions      已有 QQ 登录会话管理
     */
    public static void register(Javalin server, SiteAuthorizationService authorization, DashboardSessions sessions) {
        register(server, authorization, sessions, DashboardSettings::current);
    }

    static void register(Javalin server, SiteAuthorizationService authorization, DashboardSessions sessions,
                         Supplier<DashboardSettings> configuration) {
        server.get("/auth/authorize", ctx -> {
            DashboardSettings settings = configuration.get();
            if (!guard(ctx, settings)) return;
            if (ctx.queryString() == null || ctx.queryString().length() > 12000) {
                fail(ctx, ApiCode.AUTHORIZATION_INVALID_REQUEST);
                return;
            }
            String requestId = null;
            try {
                requestId = authorization.begin(new SiteAuthorizationService.Envelope(
                        single(ctx, "client_id"), single(ctx, "payload"), single(ctx, "signature")), settings);
                String state = sessions.beginAuthorization(requestId, settings);
                stateCookie(ctx, state, DashboardSessions.STATE_SECONDS, settings);
                ctx.redirect(settings.entryUrl() + "?authorization=" + state);
            } catch (SiteAuthorizationService.Failure failure) {
                if (requestId != null) authorization.discard(requestId);
                fail(ctx, failure.code());
            } catch (IllegalStateException busy) {
                if (requestId != null) authorization.discard(requestId);
                fail(ctx, ApiCode.AUTHORIZATION_BUSY);
            }
        });

        server.get("/auth/continue", ctx -> {
            DashboardSettings settings = configuration.get();
            if (!guard(ctx, settings)) return;
            var pending = sessions.consumeAuthorization(single(ctx, "state"), ctx.cookie("dashboard_qq_state"), settings);
            if (pending == null) {
                ctx.redirect(settings.entryUrl() + "?auth_error=LOGIN_EXPIRED");
                return;
            }
            String requestId = pending.authorizationRequestId();
            try {
                var login = sessions.authenticate(ctx.cookie(DashboardSessions.SESSION_COOKIE), settings);
                if (login != null) {
                    stateCookie(ctx, "", 0, settings);
                    ctx.redirect(authorization.complete(requestId, login.qqConnectOpenId(), login.avatarUrl(), null, settings));
                    return;
                }
                String state = sessions.beginAuthorization(requestId, settings);
                stateCookie(ctx, state, DashboardSessions.STATE_SECONDS, settings);
                String agent = ctx.userAgent() == null ? "" : ctx.userAgent().toLowerCase(Locale.ROOT);
                ctx.redirect(QqConnectClient.authorizeUrl(settings, state,
                        "1".equals(single(ctx, "mobile")) || agent.contains("mobile") || agent.contains("android")));
            } catch (SiteAuthorizationService.Failure failure) {
                if (requestId != null) authorization.discard(requestId);
                fail(ctx, failure.code());
            } catch (IllegalStateException busy) {
                if (requestId != null) authorization.discard(requestId);
                fail(ctx, ApiCode.AUTHORIZATION_BUSY);
            }
        });

        server.post("/auth/exchange", ctx -> {
            DashboardSettings settings = configuration.get();
            if (!guard(ctx, settings)) return;
            if (ctx.contentType() == null || !"application/json".equalsIgnoreCase(ctx.contentType().split(";", 2)[0].trim())) {
                fail(ctx, ApiCode.UNSUPPORTED_MEDIA_TYPE);
                return;
            }
            if (ctx.bodyAsBytes().length > 16384) {
                fail(ctx, ApiCode.PAYLOAD_TOO_LARGE);
                return;
            }
            SiteAuthorizationService.Envelope envelope;
            try {
                envelope = ctx.bodyAsClass(SiteAuthorizationService.Envelope.class);
            } catch (Exception invalid) {
                fail(ctx, ApiCode.AUTHORIZATION_INVALID_REQUEST);
                return;
            }
            try {
                var result = authorization.exchange(envelope, settings);
                ctx.header("X-Auth-Signature", result.signature()).contentType("application/json; charset=utf-8").result(result.body());
            } catch (SiteAuthorizationService.Failure failure) {
                fail(ctx, failure.code());
            } catch (Exception failure) {
                LOG.warn("站点授权码兑换失败，errorType={}", failure.getClass().getSimpleName());
                fail(ctx, ApiCode.AUTHORIZATION_FAILED);
            }
        });
    }

    private static boolean guard(Context ctx, DashboardSettings settings) {
        ctx.header("Cache-Control", "no-store").header("Pragma", "no-cache").header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff").header("X-Frame-Options", "DENY");
        if (settings.available()) return true;
        fail(ctx, ApiCode.AUTHORIZATION_DISABLED);
        return false;
    }

    private static void fail(Context ctx, ApiCode code) {
        ctx.status(code.getHttpStatus()).json(Result.failure(code));
    }

    private static void stateCookie(Context ctx, String state, int maxAge, DashboardSettings settings) {
        ctx.res().addHeader("Set-Cookie", "dashboard_qq_state=" + state + "; Path=/; Max-Age="
                + maxAge + "; HttpOnly; SameSite=Lax" + (settings.secure() ? "; Secure" : ""));
    }

    private static String single(Context ctx, String name) {
        var values = ctx.queryParams(name);
        return values.size() == 1 ? values.getFirst() : null;
    }
}
