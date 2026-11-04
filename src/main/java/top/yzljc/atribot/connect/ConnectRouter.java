package top.yzljc.atribot.connect;

import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.webui.ApiCode;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.WebUISessionManager;

/** 服务间查询接口与管理端凭证签发入口 */
@Slf4j
public final class ConnectRouter {
    public static final String BACKGROUND_PATH = "/connect/user/background";
    private ConnectRouter() {}

    public static void register(Javalin app) {
        register(app, new ConnectTicket(() -> Config.getInstance().getConnectSecret()), ConnectBackgroundService::query);
    }

    @FunctionalInterface
    interface BackgroundLookup { ConnectBackgroundService.Info query(ConnectIdentity identity) throws Exception; }

    static void register(Javalin app, ConnectTicket tickets, BackgroundLookup lookup) {
        app.beforeMatched(ctx -> {
            if (ctx.endpointHandlerPath().startsWith("/webui/api/connect/")) WebUISessionManager.require(ctx);
            if (!ctx.endpointHandlerPath().startsWith("/connect/")) return;
            ctx.header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer");
            if (!tickets.enabled()) { reject(ctx, ApiCode.SERVICE_UNAVAILABLE); return; }
            var values = ctx.queryParamMap().get("ticket");
            if (values == null || values.size() != 1 || !tickets.verify(values.getFirst()))
                reject(ctx, ApiCode.AUTHENTICATION_REQUIRED);
        });
        app.post("/webui/api/connect/ticket", ctx -> ctx.header("Cache-Control", "no-store")
                .json(Result.success(tickets.issue())));
        app.get(BACKGROUND_PATH, ctx -> {
            ConnectIdentity identity;
            try { identity = ConnectIdentity.parse(ctx.queryParamMap()); }
            catch (IllegalArgumentException e) { reject(ctx, ApiCode.INVALID_PARAMETER); return; }
            try {
                var info = lookup.query(identity);
                if (info == null) { reject(ctx, ApiCode.USER_NOT_FOUND); return; }
                ctx.json(Result.success(info));
            } catch (Exception e) {
                log.error("Connect 用户背景查询失败", e);
                reject(ctx, ApiCode.INTERNAL_ERROR);
            }
        });
    }

    private static void reject(Context ctx, ApiCode code) {
        ctx.status(code.getHttpStatus()).json(Result.failure(code));
        ctx.skipRemainingHandlers();
    }
}
