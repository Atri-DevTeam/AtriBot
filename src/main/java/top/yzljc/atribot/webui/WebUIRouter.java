package top.yzljc.atribot.webui;

import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.utils.SizeNtUid;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.webui.controller.*;

import java.io.InputStream;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
public class WebUIRouter {

    private static final String INDEX_HTML = "/official-webui/index.html";

    private static final int NTUID_RATE_LIMIT = 20;
    /** 公开查询接口频控：按 IP 60 QPM + 全局 600 QPM，滑动窗口 */
    private static final int PUBLIC_RATE_LIMIT_PER_IP = 60;
    private static final int PUBLIC_RATE_LIMIT_GLOBAL = 600;
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final Map<String, Deque<Long>> NTUID_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<String, Deque<Long>> PUBLIC_IP_REQUESTS = new ConcurrentHashMap<>();
    private static final Deque<Long> PUBLIC_GLOBAL_REQUESTS = new ConcurrentLinkedDeque<>();

    private record MetaDTO(String appId, String botOpenId) {}

    public static void register(Javalin server) {
        // 关闭状态下，静态资源/API/SPA fallback 一律不给任何响应体
        server.before("/webui", WebUIRouter::activeGuard);
        server.before("/webui/*", WebUIRouter::activeGuard);

        // 禁止被 iframe 嵌套，防点击劫持
        server.before("/webui", ctx -> ctx.header("X-Frame-Options", "DENY"));
        server.before("/webui/*", ctx -> ctx.header("X-Frame-Options", "DENY"));

        // 必须登录
        server.before("/webui/meta/*", WebUIRouter::auth);
        server.get("/webui/meta/avatar", ctx -> ctx.json(new MetaDTO(
                Config.getInstance().getQqAppId(),
                Config.getInstance().getOfficialOpenId()
        )));
        server.get("/webui/meta/name", ctx -> ctx.result(QQBot.BOT_NAME));

        // Auth middleware for webui paths.
        server.before("/webui/api/*", WebUIRouter::auth);

        registerPublicOfficialRoutes(server, "/webui/api/public/official");

        // API routes
        server.get("/webui/api/auth/challenge", AuthController::createChallenge);
        server.post("/webui/api/auth/verify", AuthController::login);
        server.post("/webui/api/auth/logout", AuthController::logout);
        server.get("/webui/api/auth/verify", AuthController::verifyToken);
        server.get("/webui/api/chat/conversations", GroupController::listChatConversations);
        server.get("/webui/api/chat/pinned", GroupController::listChatPinned);
        server.post("/webui/api/chat/pinned", GroupController::setChatPinned);
        server.post("/webui/api/chat/cleanup/orphaned-groups", GroupController::startOrphanedGroupRecordCleanup);
        server.get("/webui/api/chat/cleanup/orphaned-groups", GroupController::getOrphanedGroupRecordCleanupStatus);
        server.get("/webui/api/groups", GroupController::listGroups);
        server.get("/webui/api/groups/{groupOpenId}/messages", GroupController::fetchGroupMessages);
        server.delete("/webui/api/groups/{groupOpenId}/messages", GroupController::clearGroupMessages);
        server.get("/webui/api/groups/{groupOpenId}/messages/ref", GroupController::locateGroupMessageByRefIdx);
        server.get("/webui/api/groups/{groupOpenId}/members", GroupController::listGroupMembers);
        server.get("/webui/api/groups/{groupOpenId}/mute-state", GroupController::getGroupMuteState);
        server.post("/webui/api/groups/{groupOpenId}/mute", GroupController::muteGroupMember);
        server.post("/webui/api/groups/{groupOpenId}/unmute", GroupController::unmuteGroupMember);
        server.get("/webui/api/groups/functions/keys", GroupController::listGroupFunctionKeys);
        server.get("/webui/api/groups/{groupOpenId}/functions", GroupController::getGroupFunctions);
        server.post("/webui/api/groups/{groupOpenId}/whitelist", GroupController::setGroupWhitelist);
        server.post("/webui/api/groups/{groupOpenId}/blacklist", GroupController::setGroupBlacklist);
        server.post("/webui/api/groups/{groupOpenId}/real-group-id", GroupController::setGroupRealGroupId);
        server.post("/webui/api/groups/{groupOpenId}/profile/sync", GroupController::syncGroupProfile);
        server.post("/webui/api/groups/{groupOpenId}/functions/{functionKey}", GroupController::setGroupFunction);
        server.post("/webui/api/groups/send", GroupController::sendGroupMessage);
        server.post("/webui/api/groups/recall", GroupController::recallMessage);
        server.get("/webui/api/config", AuthController::getConfig);
        // 机器人设置：bot 资料 + config.yml 白名单项
        server.get("/webui/api/bot/profile", BotSettingsController::getProfile);
        server.get("/webui/api/bot/settings", BotSettingsController::getSettings);
        server.put("/webui/api/bot/settings", BotSettingsController::updateSettings);
        server.get("/webui/api/command-settings", CommandSettingsController::list);
        server.put("/webui/api/command-settings/{commandName}/global", CommandSettingsController::setGlobal);
        server.delete("/webui/api/command-settings/{commandName}/global", CommandSettingsController::clearGlobal);
        server.put("/webui/api/command-settings/{commandName}/groups", CommandSettingsController::setGroup);
        server.delete("/webui/api/command-settings/{commandName}/groups/{groupId}", CommandSettingsController::clearGroup);
        server.get("/webui/api/napcat/groups", NapcatController::listNapcatGroups);
        server.get("/webui/api/napcat/groups/{groupId}/features", NapcatController::getNapcatGroupFeatures);
        server.post("/webui/api/napcat/groups/{groupId}/features/{feature}", NapcatController::setNapcatGroupFeature);
        server.post("/webui/api/napcat/messages", NapcatController::fetchNapcatMessages);
        server.post("/webui/api/napcat/recall", NapcatController::recallNapcatMessages);
        server.post("/webui/api/debug/official/request", NapcatController::debugOfficialApi);
        server.get("/webui/api/function-settings", AdminController::listFunctionSettings);
        server.post("/webui/api/function-settings/{functionId}", AdminController::saveFunctionSetting);
        server.delete("/webui/api/function-settings/{functionId}", AdminController::deleteFunctionSetting);
        server.get("/webui/api/errors/list", AdminController::listErrorReports);
        server.get("/webui/api/errors/stats", AdminController::errorReportStats);
        server.get("/webui/api/errors/{traceId}", AdminController::getErrorReport);
        server.get("/webui/api/send-logs/list", AdminController::listOfficialSendLogs);
        server.get("/webui/api/send-logs/stats", AdminController::officialSendLogStats);
        server.get("/webui/api/send-logs/{id}", AdminController::getOfficialSendLog);

        // 原始事件记录
        server.get("/webui/api/event-logs/list", AdminController::listRawEventLogs);
        server.get("/webui/api/event-logs/stats", AdminController::rawEventLogStats);
        server.get("/webui/api/event-logs/{id}", AdminController::getRawEventLog);
        server.post("/webui/api/event-logs/clear", AdminController::clearRawEventLogs);

        // 入群审批：策略管理
        server.get("/webui/api/join-approval/strategies", JoinApprovalController::listApprovalStrategies);
        server.post("/webui/api/join-approval/strategies/refresh", JoinApprovalController::refreshApprovalStrategies);
        server.post("/webui/api/join-approval/strategies/create", JoinApprovalController::createApprovalStrategy);
        server.patch("/webui/api/join-approval/strategies/{strategyId}", JoinApprovalController::updateApprovalStrategy);
        server.patch("/webui/api/join-approval/strategies/{strategyId}/groups", JoinApprovalController::updateApprovalStrategyGroups);
        server.delete("/webui/api/join-approval/strategies/{strategyId}", JoinApprovalController::deleteApprovalStrategy);
        server.post("/webui/api/join-approval/strategies/{strategyId}/execute", JoinApprovalController::executeApprovalStrategy);
        server.post("/webui/api/join-approval/strategies/{strategyId}/whitelist", JoinApprovalController::updateApprovalWhitelist);
        server.get("/webui/api/join-approval/strategies/{strategyId}/whitelist", JoinApprovalController::listApprovalWhitelist);

        // 入群审批：待审批列表
        server.get("/webui/api/groups/{groupOpenId}/join-requests", JoinApprovalController::listJoinRequests);
        server.post("/webui/api/groups/{groupOpenId}/join-requests/{memberOpenId}/approve", JoinApprovalController::approveJoinRequest);
        server.post("/webui/api/groups/{groupOpenId}/join-requests/{memberOpenId}/decline", JoinApprovalController::declineJoinRequest);

        // 群管系统：违规词/AI 审核撤回 + 入群审核
        server.get("/webui/api/group-moderation/{groupOpenId}", GroupModerationController::getSettings);
        server.put("/webui/api/group-moderation/{groupOpenId}", GroupModerationController::saveSettings);
        server.get("/webui/api/group-moderation/{groupOpenId}/logs/stats", GroupModerationController::logStats);
        server.get("/webui/api/group-moderation/{groupOpenId}/logs", GroupModerationController::listLogs);

        // C2C 私聊
        server.get("/webui/api/c2c/users", C2CController::listC2CUsers);
        server.get("/webui/api/c2c/{userOpenId}/permissions", C2CController::getC2CUserPermissions);
        server.post("/webui/api/c2c/{userOpenId}/profile", C2CController::updateC2CUserProfile);
        server.post("/webui/api/c2c/{userOpenId}/role", C2CController::setC2CUserRole);
        server.post("/webui/api/c2c/{userOpenId}/permissions/{permission}", C2CController::toggleC2CUserPermission);
        server.post("/webui/api/c2c/{userOpenId}/blocked", C2CController::setC2CUserBlocked);
        server.post("/webui/api/c2c/{userOpenId}/ignored", C2CController::setC2CUserIgnored);
        server.post("/webui/api/c2c/{userOpenId}/push", C2CController::setC2CUserPush);
        server.get("/webui/api/c2c/{userOpenId}/messages", C2CController::fetchC2CMessages);
        server.delete("/webui/api/c2c/{userOpenId}/messages", C2CController::clearC2CMessages);
        server.get("/webui/api/c2c/{userOpenId}/messages/ref", C2CController::locateC2CMessageByRefIdx);
        server.post("/webui/api/c2c/send", C2CController::sendC2CMessage);
        server.post("/webui/api/c2c/recall", C2CController::recallC2CMessage);
        server.post("/webui/api/c2c/stream", C2CController::sendC2CStreamMessage);

        // 自定义菜单 + 指令面板
        server.get("/webui/api/menu", MenuController::queryMenu);
        server.put("/webui/api/menu", MenuController::updateMenu);
        server.get("/webui/api/panels", PanelController::listPanels);
        server.post("/webui/api/panels", PanelController::createPanel);
        server.get("/webui/api/panels/{panelId}", PanelController::getPanelDetail);
        server.put("/webui/api/panels/{panelId}", PanelController::updatePanel);
        server.delete("/webui/api/panels/{panelId}", PanelController::deletePanel);
        server.put("/webui/api/panels/{panelId}/target", PanelController::updatePanelTarget);

        server.get("/webui/api/events", SseBroadcaster::handle);

        // 反馈管理
        server.get("/webui/api/feedback/list", AdminController::listFeedback);
        server.get("/webui/api/feedback/count", AdminController::countFeedback);
        server.post("/webui/api/feedback/reply", AdminController::replyFeedback);

        // 图源管理
        server.get("/webui/api/gallery/list", ContentController::listGallery);
        server.get("/webui/api/gallery/count", ContentController::countGallery);
        server.post("/webui/api/gallery/review", ContentController::reviewGallery);
        server.post("/webui/api/gallery/review-batch", ContentController::reviewGalleryBatch);
        server.post("/webui/api/gallery/delete", ContentController::deleteGallery);

        // Minecraft 玩家名审核
        server.post("/webui/api/minecraft-moderation/players", MinecraftWhitelistController::submitPlayer);
        server.get("/webui/api/minecraft-moderation/names", MinecraftWhitelistController::listNames);
        server.get("/webui/api/minecraft-moderation/skins", MinecraftWhitelistController::listSkins);
        server.put("/webui/api/minecraft-moderation/names/{id}", MinecraftWhitelistController::reviewName);
        server.put("/webui/api/minecraft-moderation/skins/{id}", MinecraftWhitelistController::reviewSkin);
        server.get("/webui/api/minecraft-moderation/skins/{skinId}/preview/{type}", MinecraftWhitelistController::skinPreview);

        // 抽卡系统管理
        server.get("/webui/api/loot/items", ContentController::listLootItems);
        server.post("/webui/api/loot/items", ContentController::createLootItem);
        server.put("/webui/api/loot/items/{itemId}", ContentController::updateLootItem);
        server.post("/webui/api/loot/items/{itemId}/image", ContentController::replaceLootItemImage);
        server.delete("/webui/api/loot/items/{itemId}", ContentController::deleteLootItem);
        server.get("/webui/api/loot/coins/leaderboard", ContentController::listCoinLeaderboard);
        server.post("/webui/api/loot/coins/{userId}", ContentController::adjustUserCoins);
        server.get("/webui/api/loot/users", ContentController::listLootUsers);
        server.get("/webui/api/loot/users/{userId}", ContentController::getUserLootsDetail);
        server.post("/webui/api/loot/users/{userId}/grant", ContentController::grantUserLoot);
        server.post("/webui/api/loot/users/{userId}/grant-batch", ContentController::grantUserLootBatch);
        server.post("/webui/api/loot/users/{userId}/loots/revoke-batch", ContentController::revokeUserLootBatch);
        server.post("/webui/api/loot/users/{userId}/loots/set-special", ContentController::setUserLootsSpecial);
        server.post("/webui/api/loot/users/{userId}/loots/{itemId}/revoke-all", ContentController::revokeUserLootAll);
        server.delete("/webui/api/loot/users/{userId}/loots/{itemId}", ContentController::revokeUserLoot);

        server.error(404, WebUIRouter::spaFallback);
    }

    private static void ntUidRateLimit(Context ctx) {
        Deque<Long> timestamps = NTUID_REQUESTS.computeIfAbsent(ctx.ip(), _ -> new ConcurrentLinkedDeque<>());
        if (!tryAcquireRateSlot(timestamps, NTUID_RATE_LIMIT, RATE_WINDOW_MS, System.currentTimeMillis())) {
            ctx.status(429).json(Result.fail(429, "超出接口频控限制"));
            ctx.skipRemainingHandlers();
            return;
        }
        cleanupRateBuckets(NTUID_REQUESTS, System.currentTimeMillis());
    }

    /** 公开查询接口双层频控：先全局后按 IP；超限的全局请求不触碰 per-IP 表，防止伪造 IP 撑大内存 */
    private static void publicRateLimit(Context ctx) {
        long now = System.currentTimeMillis();
        if (!tryAcquireRateSlot(PUBLIC_GLOBAL_REQUESTS, PUBLIC_RATE_LIMIT_GLOBAL, RATE_WINDOW_MS, now)) {
            rejectTooManyRequests(ctx);
            return;
        }
        Deque<Long> ipSlots = PUBLIC_IP_REQUESTS.computeIfAbsent(ctx.ip(), _ -> new ConcurrentLinkedDeque<>());
        if (!tryAcquireRateSlot(ipSlots, PUBLIC_RATE_LIMIT_PER_IP, RATE_WINDOW_MS, now)) {
            rejectTooManyRequests(ctx);
            return;
        }
        cleanupRateBuckets(PUBLIC_IP_REQUESTS, now);
    }

    private static void rejectTooManyRequests(Context ctx) {
        ctx.status(429).json(Result.fail(429, "请求过于频繁，请稍后再试"));
        ctx.skipRemainingHandlers();
    }

    /** 滑动窗口取一个频控名额；窗口内已满则返回 false 且不占用名额 */
    private static boolean tryAcquireRateSlot(Deque<Long> timestamps, int limit, long windowMs, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= limit) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }

    /** 频控桶总量超过阈值时，移除整个窗口内无请求的 IP，防止伪造 IP 无限撑大内存 */
    private static void cleanupRateBuckets(Map<String, Deque<Long>> buckets, long now) {
        if (buckets.size() < 10_000) {
            return;
        }
        long cutoff = now - RATE_WINDOW_MS;
        buckets.entrySet().removeIf(entry -> {
            Long last = entry.getValue().peekLast();
            return last == null || last < cutoff;
        });
    }

    private static void activeGuard(Context ctx) {
        if (ctx.path().contains("/api/public/")) {
            return;
        }
        if (!WebUISessionManager.isActive()) {
            if (!ctx.path().contains("event")) {
                log.warn("WebUI 未开启，拦截请求: {}", ctx.path());
            }
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
        server.before(prefix + "/*", WebUIRouter::publicRateLimit);
        server.get(prefix + "/group/messages/received", PublicQueryController::publicOfficialGroupReceivedMessages);
        server.get(prefix + "/group/messages/sent", PublicQueryController::publicOfficialGroupSentMessages);
        server.get(prefix + "/c2c/messages/received", PublicQueryController::publicOfficialC2CReceivedMessages);
        server.get(prefix + "/c2c/messages/sent", PublicQueryController::publicOfficialC2CSentMessages);
        server.get(prefix + "/dau", PublicQueryController::publicOfficialDau);
        server.get(prefix + "/series", PublicQueryController::publicOfficialSeries);
        server.get(prefix + "/sign", PublicQueryController::publicOfficialSign);
        server.get(prefix + "/users/{userOpenId}", PublicQueryController::publicOfficialUserInfo);
        server.get(prefix + "/groups/{groupOpenId}", PublicQueryController::publicOfficialGroupInfo);
        server.before(prefix + "/ntuid", WebUIRouter::ntUidRateLimit);
        server.post(prefix + "/ntuid", SizeNtUid::ntUidController);
    }
}
