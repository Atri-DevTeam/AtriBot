package top.yzljc.atribot.webui.impl;

import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.napcat.SizeNtUid;
import top.yzljc.atribot.platform.official.OfficialBot;
import top.yzljc.atribot.webui.Result;

import java.io.InputStream;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
public class WebUIRouter {

    private static final String INDEX_HTML = "/official-webui/index.html";

    private static final int NTUID_RATE_LIMIT = 20;
    private static final long NTUID_RATE_WINDOW_MS = 60_000L;
    private static final Map<String, Deque<Long>> NTUID_REQUESTS = new ConcurrentHashMap<>();

    private record MetaDTO(String appId, String botOpenId) {}

    public static void register(Javalin server) {
        // 关闭状态下，静态资源/API/SPA fallback 一律不给任何响应体。
        server.before("/webui", WebUIRouter::activeGuard);
        server.before("/webui/*", WebUIRouter::activeGuard);

        // 兼容旧前端的元数据接口，但现在也必须先登录。
        server.before("/webui/meta/*", WebUIRouter::auth);
        server.get("/webui/meta/avatar", ctx -> ctx.json(new MetaDTO(
                Config.getInstance().getQqAppId(),
                Config.getInstance().getOfficialOpenId()
        )));
        server.get("/webui/meta/name", ctx -> ctx.result(OfficialBot.BOT_NAME));

        // Auth middleware for webui paths.
        server.before("/webui/api/*", WebUIRouter::auth);

        registerPublicOfficialRoutes(server, "/webui/api/public/official");

        // API routes
        server.get("/webui/api/auth/challenge", WebUIController::createChallenge);
        server.post("/webui/api/auth/verify", WebUIController::login);
        server.post("/webui/api/auth/logout", WebUIController::logout);
        server.get("/webui/api/auth/verify", WebUIController::verifyToken);
        server.get("/webui/api/chat/conversations", WebUIController::listChatConversations);
        server.get("/webui/api/chat/pinned", WebUIController::listChatPinned);
        server.post("/webui/api/chat/pinned", WebUIController::setChatPinned);
        server.get("/webui/api/groups", WebUIController::listGroups);
        server.get("/webui/api/groups/{groupOpenId}/messages", WebUIController::fetchGroupMessages);
        server.get("/webui/api/groups/{groupOpenId}/messages/ref", WebUIController::locateGroupMessageByRefIdx);
        server.get("/webui/api/groups/{groupOpenId}/members", WebUIController::listGroupMembers);
        server.get("/webui/api/groups/functions/keys", WebUIController::listGroupFunctionKeys);
        server.get("/webui/api/groups/{groupOpenId}/functions", WebUIController::getGroupFunctions);
        server.post("/webui/api/groups/{groupOpenId}/whitelist", WebUIController::setGroupWhitelist);
        server.post("/webui/api/groups/{groupOpenId}/blacklist", WebUIController::setGroupBlacklist);
        server.post("/webui/api/groups/{groupOpenId}/real-group-id", WebUIController::setGroupRealGroupId);
        server.post("/webui/api/groups/{groupOpenId}/functions/{functionKey}", WebUIController::setGroupFunction);
        server.post("/webui/api/groups/send", WebUIController::sendGroupMessage);
        server.post("/webui/api/groups/recall", WebUIController::recallMessage);
        server.get("/webui/api/config", WebUIController::getConfig);
        server.get("/webui/api/napcat/groups", WebUIController::listNapcatGroups);
        server.get("/webui/api/napcat/groups/{groupId}/features", WebUIController::getNapcatGroupFeatures);
        server.post("/webui/api/napcat/groups/{groupId}/features/{feature}", WebUIController::setNapcatGroupFeature);
        server.post("/webui/api/napcat/messages", WebUIController::fetchNapcatMessages);
        server.post("/webui/api/napcat/recall", WebUIController::recallNapcatMessages);
        server.post("/webui/api/debug/official/request", WebUIController::debugOfficialApi);
        server.get("/webui/api/errors/list", WebUIController::listErrorReports);
        server.get("/webui/api/errors/stats", WebUIController::errorReportStats);
        server.get("/webui/api/errors/{traceId}", WebUIController::getErrorReport);
        server.get("/webui/api/send-logs/list", WebUIController::listOfficialSendLogs);
        server.get("/webui/api/send-logs/stats", WebUIController::officialSendLogStats);
        server.get("/webui/api/send-logs/{id}", WebUIController::getOfficialSendLog);

        // C2C 私聊
        server.get("/webui/api/c2c/users", WebUIController::listC2CUsers);
        server.get("/webui/api/c2c/{userOpenId}/permissions", WebUIController::getC2CUserPermissions);
        server.post("/webui/api/c2c/{userOpenId}/profile", WebUIController::updateC2CUserProfile);
        server.post("/webui/api/c2c/{userOpenId}/role", WebUIController::setC2CUserRole);
        server.post("/webui/api/c2c/{userOpenId}/permissions/{permission}", WebUIController::toggleC2CUserPermission);
        server.post("/webui/api/c2c/{userOpenId}/blocked", WebUIController::setC2CUserBlocked);
        server.post("/webui/api/c2c/{userOpenId}/ignored", WebUIController::setC2CUserIgnored);
        server.post("/webui/api/c2c/{userOpenId}/push", WebUIController::setC2CUserPush);
        server.delete("/webui/api/c2c/{userOpenId}", WebUIController::deleteC2CUser);
        server.get("/webui/api/c2c/{userOpenId}/messages", WebUIController::fetchC2CMessages);
        server.get("/webui/api/c2c/{userOpenId}/messages/ref", WebUIController::locateC2CMessageByRefIdx);
        server.post("/webui/api/c2c/send", WebUIController::sendC2CMessage);
        server.post("/webui/api/c2c/recall", WebUIController::recallC2CMessage);
        server.post("/webui/api/c2c/stream", WebUIController::sendC2CStreamMessage);

        server.get("/webui/api/events", SseBroadcaster::handle);

        // 反馈管理
        server.get("/webui/api/feedback/list", WebUIController::listFeedback);
        server.get("/webui/api/feedback/count", WebUIController::countFeedback);
        server.post("/webui/api/feedback/reply", WebUIController::replyFeedback);

        // 图源管理
        server.get("/webui/api/gallery/list", WebUIController::listGallery);
        server.get("/webui/api/gallery/count", WebUIController::countGallery);
        server.post("/webui/api/gallery/review", WebUIController::reviewGallery);
        server.post("/webui/api/gallery/review-batch", WebUIController::reviewGalleryBatch);
        server.post("/webui/api/gallery/delete", WebUIController::deleteGallery);

        // 用户列表
        server.get("/webui/api/users/messages", WebUIController::listUserMessages);
        server.get("/webui/api/users/c2c-messages", WebUIController::listUserC2CMessages);

        server.error(404, WebUIRouter::spaFallback);
    }

    private static void ntUidRateLimit(Context ctx) {
        String ip = ctx.ip();
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = NTUID_REQUESTS.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > NTUID_RATE_WINDOW_MS) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= NTUID_RATE_LIMIT) {
            ctx.status(429).json(Result.fail(429, "超出接口频控限制"));
            ctx.skipRemainingHandlers();
            return;
        }
        timestamps.addLast(now);
    }

    private static void activeGuard(Context ctx) {
        if (ctx.path().contains("/api/public/")) {
            return;
        }
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
        if (path.contains("/api/public/")) {
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

    private static void spaFallback(Context ctx) {
        String path = ctx.path();
        if (!path.startsWith("/webui/")) return;
        if (path.contains("/api/")) return;
        if (!WebUISessionManager.isActive()) {
            ctx.status(503).result("");
            return;
        }
        InputStream in = WebUIRouter.class.getResourceAsStream(INDEX_HTML);
        if (in != null) {
            ctx.status(200);
            ctx.contentType("text/html; charset=utf-8");
            ctx.result(in);
        }
    }

    private static void registerPublicOfficialRoutes(Javalin server, String prefix) {
        server.get(prefix + "/group/messages/received", WebUIController::publicOfficialGroupReceivedMessages);
        server.get(prefix + "/group/messages/sent", WebUIController::publicOfficialGroupSentMessages);
        server.get(prefix + "/c2c/messages/received", WebUIController::publicOfficialC2CReceivedMessages);
        server.get(prefix + "/c2c/messages/sent", WebUIController::publicOfficialC2CSentMessages);
        server.get(prefix + "/dau", WebUIController::publicOfficialDau);
        server.get(prefix + "/series", WebUIController::publicOfficialSeries);
        server.get(prefix + "/users/{userOpenId}", WebUIController::publicOfficialUserInfo);
        server.get(prefix + "/groups/{groupOpenId}", WebUIController::publicOfficialGroupInfo);
        server.before(prefix + "/ntuid", WebUIRouter::ntUidRateLimit);
        server.post(prefix + "/ntuid", SizeNtUid::ntUidController);
    }
}
