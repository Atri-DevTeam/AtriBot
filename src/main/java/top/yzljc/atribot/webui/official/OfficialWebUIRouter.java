package top.yzljc.atribot.webui.official;

import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.webui.Result;

import java.io.InputStream;

@Slf4j
public class OfficialWebUIRouter {

    private static final String INDEX_HTML = "/official-webui/index.html";

    private record MetaDTO(String appId, String botOpenId) {}

    public static void register(Javalin server) {
        // 会话守卫 — 关闭时拒绝，开启时刷新倒计时
        server.before("/official-webui/*", ctx -> {
            if (!WebUISessionManager.isActive()) {
                log.warn("WebUI 未开启，拦截请求: {}", ctx.path());
                ctx.status(503).result("");
            }
        });
        server.before("/webui/*", ctx -> {
            if (!WebUISessionManager.isActive()) {
                log.warn("WebUI 未开启，拦截请求: {}", ctx.path());
                ctx.status(503).result("");
            }
        });

        // 公开元数据 — 不需要 auth，给登录页用
        server.get("/webui/meta/avatar", ctx -> ctx.json(new MetaDTO(
                Config.getInstance().getQqAppId(),
                Config.getInstance().getOfficialOpenId()
        )));
        server.get("/webui/meta/name", ctx -> ctx.result(Config.getInstance().getOfficialUsername()));

        // Auth middleware for both paths
        server.before("/official-webui/api/*", OfficialWebUIRouter::auth);
        server.before("/webui/api/*", OfficialWebUIRouter::auth);

        // API routes — both paths
        server.get("/official-webui/api/auth/verify", OfficialWebUIController::verifyToken);
        server.get("/official-webui/api/groups", OfficialWebUIController::listGroups);
        server.get("/official-webui/api/groups/{groupOpenId}/messages", OfficialWebUIController::fetchGroupMessages);
        server.post("/official-webui/api/groups/send", OfficialWebUIController::sendGroupMessage);
        server.get("/official-webui/api/config", OfficialWebUIController::getConfig);

        server.get("/webui/api/auth/verify", OfficialWebUIController::verifyToken);
        server.get("/webui/api/groups", OfficialWebUIController::listGroups);
        server.get("/webui/api/groups/{groupOpenId}/messages", OfficialWebUIController::fetchGroupMessages);
        server.post("/webui/api/groups/send", OfficialWebUIController::sendGroupMessage);
        server.post("/webui/api/groups/recall", OfficialWebUIController::recallMessage);
        server.get("/webui/api/config", OfficialWebUIController::getConfig);

        // C2C 私聊
        server.get("/webui/api/c2c/users", OfficialWebUIController::listC2CUsers);
        server.get("/webui/api/c2c/{userOpenId}/messages", OfficialWebUIController::fetchC2CMessages);
        server.post("/webui/api/c2c/send", OfficialWebUIController::sendC2CMessage);

        server.get("/webui/api/events", SseBroadcaster::handle);

        server.error(404, OfficialWebUIRouter::spaFallback);
    }

    private static void auth(io.javalin.http.Context ctx) {
        String token = Config.getInstance().getOfficialWebuiToken();
        if (token == null || token.isBlank() || "null".equalsIgnoreCase(token)) {
            ctx.status(401).json(Result.fail(401, "Official WebUI Token 未配置"));
            return;
        }

        // 优先 Authorization header，SSE 用 cookie
        String authorization = ctx.header("Authorization");
        if (authorization != null) {
            if (!("Bearer " + token).equals(authorization)) {
                ctx.status(401).json(Result.fail(401, "未授权"));
            }
            return;
        }
        if (!token.equals(ctx.cookie("webui_token"))) {
            ctx.status(401).json(Result.fail(401, "未授权"));
        }
    }

    private static void spaFallback(io.javalin.http.Context ctx) {
        String path = ctx.path();
        if (!path.startsWith("/webui/") && !path.startsWith("/official-webui/")) return;
        if (path.contains("/api/")) return;
        InputStream in = OfficialWebUIRouter.class.getResourceAsStream(INDEX_HTML);
        if (in != null) {
            ctx.status(200);
            ctx.contentType("text/html; charset=utf-8");
            ctx.result(in);
        }
    }
}
