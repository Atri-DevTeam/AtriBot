package top.yzljc.atribot.webui.official;

import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.webui.Result;

import java.io.InputStream;

@Slf4j
public class OfficialWebUIRouter {

    private static final String INDEX_HTML = "/official-webui/index.html";

    private record MetaDTO(String appId, String botOpenId) {}

    public static void register(Javalin server) {
        // 关闭状态下，静态资源/API/SPA fallback 一律不给任何响应体。
        server.before("/official-webui", OfficialWebUIRouter::activeGuard);
        server.before("/official-webui/*", OfficialWebUIRouter::activeGuard);
        server.before("/webui", OfficialWebUIRouter::activeGuard);
        server.before("/webui/*", OfficialWebUIRouter::activeGuard);

        // 兼容旧前端的元数据接口，但现在也必须先登录。
        server.before("/webui/meta/*", OfficialWebUIRouter::auth);
        server.get("/webui/meta/avatar", ctx -> ctx.json(new MetaDTO(
                Config.getInstance().getQqAppId(),
                Config.getInstance().getOfficialOpenId()
        )));
        server.get("/webui/meta/name", ctx -> ctx.result(Config.getInstance().getOfficialUsername()));

        // Auth middleware for official webui and legacy onebot webui paths.
        server.before("/official-webui/api/*", OfficialWebUIRouter::auth);
        server.before("/webui/api/*", OfficialWebUIRouter::auth);
        server.before("/webui/v1/*", OfficialWebUIRouter::auth);

        // API routes — both paths
        server.get("/official-webui/api/auth/challenge", OfficialWebUIController::createChallenge);
        server.post("/official-webui/api/auth/verify", OfficialWebUIController::login);
        server.post("/official-webui/api/auth/logout", OfficialWebUIController::logout);
        server.get("/official-webui/api/auth/verify", OfficialWebUIController::verifyToken);
        server.get("/official-webui/api/groups", OfficialWebUIController::listGroups);
        server.get("/official-webui/api/groups/{groupOpenId}/messages", OfficialWebUIController::fetchGroupMessages);
        server.post("/official-webui/api/groups/send", OfficialWebUIController::sendGroupMessage);
        server.get("/official-webui/api/config", OfficialWebUIController::getConfig);

        server.get("/webui/api/auth/challenge", OfficialWebUIController::createChallenge);
        server.post("/webui/api/auth/verify", OfficialWebUIController::login);
        server.post("/webui/api/auth/logout", OfficialWebUIController::logout);
        server.get("/webui/api/auth/verify", OfficialWebUIController::verifyToken);
        server.get("/webui/api/groups", OfficialWebUIController::listGroups);
        server.get("/webui/api/groups/{groupOpenId}/messages", OfficialWebUIController::fetchGroupMessages);
        server.get("/webui/api/groups/{groupOpenId}/functions", OfficialWebUIController::getGroupFunctions);
        server.post("/webui/api/groups/{groupOpenId}/whitelist", OfficialWebUIController::setGroupWhitelist);
        server.post("/webui/api/groups/{groupOpenId}/blacklist", OfficialWebUIController::setGroupBlacklist);
        server.post("/webui/api/groups/{groupOpenId}/allowed-active", OfficialWebUIController::setGroupAllowedActive);
        server.post("/webui/api/groups/{groupOpenId}/functions/{functionKey}", OfficialWebUIController::setGroupFunction);
        server.post("/webui/api/groups/send", OfficialWebUIController::sendGroupMessage);
        server.post("/webui/api/groups/recall", OfficialWebUIController::recallMessage);
        server.get("/webui/api/config", OfficialWebUIController::getConfig);

        // C2C 私聊
        server.get("/webui/api/c2c/users", OfficialWebUIController::listC2CUsers);
        server.get("/webui/api/c2c/{userOpenId}/permissions", OfficialWebUIController::getC2CUserPermissions);
        server.post("/webui/api/c2c/{userOpenId}/role", OfficialWebUIController::setC2CUserRole);
        server.post("/webui/api/c2c/{userOpenId}/permissions/{permission}", OfficialWebUIController::toggleC2CUserPermission);
        server.get("/webui/api/c2c/{userOpenId}/messages", OfficialWebUIController::fetchC2CMessages);
        server.post("/webui/api/c2c/send", OfficialWebUIController::sendC2CMessage);
        server.post("/webui/api/c2c/recall", OfficialWebUIController::recallC2CMessage);

        server.get("/webui/api/events", SseBroadcaster::handle);

        // 反馈管理
        server.get("/webui/api/feedback/list", OfficialWebUIController::listFeedback);
        server.get("/webui/api/feedback/count", OfficialWebUIController::countFeedback);
        server.post("/webui/api/feedback/reply", OfficialWebUIController::replyFeedback);

        server.error(404, OfficialWebUIRouter::spaFallback);
    }

    private static void activeGuard(io.javalin.http.Context ctx) {
        if (!WebUISessionManager.isActive()) {
            log.warn("WebUI 未开启，拦截请求: {}", ctx.path());
            ctx.status(503).result("");
            ctx.skipRemainingHandlers();
        }
    }

    private static void auth(io.javalin.http.Context ctx) {
        String path = ctx.path();
        String method = ctx.method().toString();
        if (path.endsWith("/api/auth/challenge") || (path.endsWith("/api/auth/verify") && "POST".equalsIgnoreCase(method))) {
            return;
        }

        String token = Config.getInstance().getOfficialWebuiToken();
        if (token == null || token.isBlank() || "null".equalsIgnoreCase(token)) {
            ctx.status(401).json(Result.fail(401, "Official WebUI Token 未配置"));
            ctx.skipRemainingHandlers();
            return;
        }

        if (!WebUISessionManager.verifySession(ctx.cookie(WebUISessionManager.SESSION_COOKIE))) {
            ctx.status(401).json(Result.fail(401, "未授权"));
            ctx.skipRemainingHandlers();
        }
    }

    private static void spaFallback(io.javalin.http.Context ctx) {
        String path = ctx.path();
        if (!path.startsWith("/webui/") && !path.startsWith("/official-webui/")) return;
        if (path.contains("/api/")) return;
        if (!WebUISessionManager.isActive()) {
            ctx.status(503).result("");
            return;
        }
        InputStream in = OfficialWebUIRouter.class.getResourceAsStream(INDEX_HTML);
        if (in != null) {
            ctx.status(200);
            ctx.contentType("text/html; charset=utf-8");
            ctx.result(in);
        }
    }
}
