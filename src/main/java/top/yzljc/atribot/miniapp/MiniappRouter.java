package top.yzljc.atribot.miniapp;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import top.yzljc.atribot.miniapp.service.MiniappActivityService;
import top.yzljc.atribot.miniapp.service.MiniappInventoryImageService;
import top.yzljc.atribot.miniapp.service.MiniappProfileService;
import top.yzljc.atribot.miniapp.service.MiniappGroupService;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappRouter
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
public final class MiniappRouter {
    private MiniappRouter() {
    }

    public record ExchangeRequest(String userId, String ticket) {
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupBindingRequest(String groupId) {}

    public static void register(Javalin server, MiniappSessions sessions, boolean enabled) {
        register(server, sessions, enabled, new MiniappProfileService()::load);
    }

    static void register(Javalin server, MiniappSessions sessions, boolean enabled,
                         Function<MiniappSessions.Identity, MiniappProfileService.Profile> profiles) {
        register(server, sessions, enabled, profiles, new MiniappActivityService());
    }

    static void register(Javalin server, MiniappSessions sessions, boolean enabled,
                         Function<MiniappSessions.Identity, MiniappProfileService.Profile> profiles,
                         MiniappActivityService activity) {
        register(server, sessions, enabled, profiles, activity, new MiniappInventoryImageService());
    }

    static void register(Javalin server, MiniappSessions sessions, boolean enabled,
                         Function<MiniappSessions.Identity, MiniappProfileService.Profile> profiles,
                         MiniappActivityService activity, MiniappInventoryImageService inventoryImages) {
        register(server, sessions, enabled, profiles, activity, inventoryImages, MiniappGroupService.INSTANCE);
    }

    static void register(Javalin server, MiniappSessions sessions, boolean enabled,
                         Function<MiniappSessions.Identity, MiniappProfileService.Profile> profiles,
                         MiniappActivityService activity, MiniappInventoryImageService inventoryImages,
                         MiniappGroupService groups) {
        server.before("/atrimeow/profile", ctx -> guard(ctx, enabled));
        server.before("/atrimeow/profile/*", ctx -> guard(ctx, enabled));
        server.get("/atrimeow/profile", MiniappRouter::index);
        server.get("/atrimeow/profile/", MiniappRouter::index);

        // GET/HEAD only load the public shell. Link previews do not consume tickets.
        server.post("/atrimeow/profile/api/auth/exchange", ctx -> {
            if (ctx.contentType() == null || !Objects.requireNonNull(ctx.contentType()).toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
                throw new BadRequestResponse("Expected JSON");
            }
            ExchangeRequest request = ctx.bodyAsClass(ExchangeRequest.class);
            if (request == null || request.userId() == null || request.userId().length() > 256
                    || request.ticket() == null || request.ticket().length() > 128) throw new BadRequestResponse();
            MiniappSessions.Access access = sessions.exchange(request.ticket(), request.userId());
            if (access == null) {
                ctx.status(401).json(Map.of("error", "ENTRY_EXPIRED"));
                return;
            }
            ctx.json(access);
        });
        server.get("/atrimeow/profile/api/me", ctx -> ctx.json(requireIdentity(ctx, sessions)));
        server.get("/atrimeow/profile/api/profile", ctx -> ctx.json(profiles.apply(requireIdentity(ctx, sessions))));
        server.get("/atrimeow/profile/api/activity", ctx -> ctx.json(activity.load(requireIdentity(ctx, sessions))));
        server.get("/atrimeow/profile/api/groups", ctx -> {
            var identity = requireIdentity(ctx, sessions);
            try { ctx.json(groups.load(identity)); }
            catch (java.sql.SQLException e) {
                org.slf4j.LoggerFactory.getLogger(MiniappRouter.class).warn("群资料读取失败", e);
                ctx.status(502).json(Map.of("error", "GROUPS_UNAVAILABLE"));
            }
        });
        server.post("/atrimeow/profile/api/groups/binding", ctx -> {
            var identity = requireIdentity(ctx, sessions);
            if (ctx.contentType() == null || !ctx.contentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
                throw new BadRequestResponse("Expected JSON");
            GroupBindingRequest request;
            try { request = ctx.bodyAsClass(GroupBindingRequest.class); }
            catch (Exception invalid) { throw new BadRequestResponse("Invalid binding request"); }
            if (request == null) throw new BadRequestResponse();
            try { ctx.json(groups.start(identity, request.groupId())); }
            catch (MiniappGroupService.Problem problem) { ctx.status(problem.status).json(Map.of("error", problem.code)); }
            catch (java.sql.SQLException e) {
                org.slf4j.LoggerFactory.getLogger(MiniappRouter.class).warn("发起群绑定失败", e);
                ctx.status(502).json(Map.of("error", "GROUPS_UNAVAILABLE"));
            }
        });
        server.get("/atrimeow/profile/api/groups/{groupId}", ctx -> {
            var identity = requireIdentity(ctx, sessions);
            try { ctx.json(groups.detail(identity, ctx.pathParam("groupId"))); }
            catch (MiniappGroupService.Problem problem) { ctx.status(problem.status).json(Map.of("error", problem.code)); }
            catch (java.sql.SQLException e) {
                org.slf4j.LoggerFactory.getLogger(MiniappRouter.class).warn("群详情读取失败", e);
                ctx.status(502).json(Map.of("error", "GROUPS_UNAVAILABLE"));
            }
        });
        server.get("/atrimeow/profile/api/groups/{groupId}/join-welcome", ctx -> {
            var identity = requireIdentity(ctx, sessions);
            try { ctx.json(groups.welcome(identity, ctx.pathParam("groupId"))); }
            catch (MiniappGroupService.Problem problem) { ctx.status(problem.status).json(Map.of("error", problem.code)); }
            catch (java.sql.SQLException e) {
                org.slf4j.LoggerFactory.getLogger(MiniappRouter.class).warn("群欢迎内容读取失败", e);
                ctx.status(502).json(Map.of("error", "WELCOME_UNAVAILABLE"));
            }
        });
        server.post("/atrimeow/profile/api/groups/unbinding", ctx -> {
            var identity = requireIdentity(ctx, sessions);
            if (ctx.contentType() == null || !ctx.contentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
                throw new BadRequestResponse("Expected JSON");
            GroupBindingRequest request;
            try { request = ctx.bodyAsClass(GroupBindingRequest.class); }
            catch (Exception invalid) { throw new BadRequestResponse("Invalid unbinding request"); }
            if (request == null) throw new BadRequestResponse();
            try { groups.unbind(identity, request.groupId()); ctx.json(Map.of("success", true)); }
            catch (MiniappGroupService.Problem problem) { ctx.status(problem.status).json(Map.of("error", problem.code)); }
            catch (java.sql.SQLException e) {
                org.slf4j.LoggerFactory.getLogger(MiniappRouter.class).warn("解除群绑定失败", e);
                ctx.status(502).json(Map.of("error", "GROUPS_UNAVAILABLE"));
            }
        });
        server.get("/atrimeow/profile/api/inventory", ctx -> {
            var identity = requireIdentity(ctx, sessions);
            ctx.json(activity.inventory(identity, offset(ctx)));
        });
        server.get("/atrimeow/profile/api/inventory/image", ctx -> {
            var identity = requireIdentity(ctx, sessions);
            MiniappInventoryImageService.Picture picture;
            try {
                picture = inventoryImages.render(identity);
            } catch (java.io.IOException e) {
                org.slf4j.LoggerFactory.getLogger(MiniappRouter.class).warn("Miniapp 背包图读取失败", e);
                ctx.status(502).json(Map.of("error", "INVENTORY_IMAGE_UNAVAILABLE"));
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ctx.status(502).json(Map.of("error", "INVENTORY_IMAGE_UNAVAILABLE"));
                return;
            }
            // 渲染期间退出的会话不能继续获取私有图片。
            requireIdentity(ctx, sessions);
            ctx.contentType(picture.contentType()).result(picture.bytes());
        });
        server.get("/atrimeow/profile/api/gains", ctx -> {
            var identity = requireIdentity(ctx, sessions);
            ctx.json(activity.gains(identity, offset(ctx)));
        });
        server.post("/atrimeow/profile/api/session/heartbeat", ctx -> {
            requireIdentity(ctx, sessions);
            ctx.status(204);
        });
        // sendBeacon cannot set Authorization; this endpoint only revokes, never authorizes.
        server.post("/atrimeow/profile/api/session/close", ctx -> {
            String token = bearer(ctx);
            if (token == null) token = ctx.body();
            if (token.length() <= 128) sessions.revoke(token);
            ctx.status(204);
        });
    }

    private static void guard(Context ctx, boolean enabled) {
        ctx.header("Cache-Control", "no-store");
        ctx.header("Referrer-Policy", "no-referrer");
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("X-Frame-Options", "DENY");
        if (!enabled) {
            ctx.status(503).json(Map.of("error", "MINIAPP_DISABLED"));
            ctx.skipRemainingHandlers();
        }
    }

    private static int offset(Context ctx) {
        String value = ctx.queryParam("offset");
        if (value == null) return 0;
        try {
            int offset = Integer.parseInt(value);
            if (offset >= 0 && offset <= 1_000_000) return offset;
        } catch (NumberFormatException ignored) {
        }
        throw new BadRequestResponse("Invalid offset");
    }

    private static void index(Context ctx) {
        InputStream stream = MiniappRouter.class.getResourceAsStream("/miniapp/index.html");
        if (stream == null) {
            ctx.status(503).result("Miniapp assets have not been built.");
            return;
        }
        ctx.contentType("text/html; charset=utf-8").result(stream);
    }

    /**
     * Every future private API must derive identity here, never from a request userId.
     */
    public static MiniappSessions.Identity requireIdentity(Context ctx, MiniappSessions sessions) {
        MiniappSessions.Identity identity = sessions.authenticate(bearer(ctx));
        if (identity == null) throw new UnauthorizedResponse("PAGE_SESSION_EXPIRED");
        return identity;
    }

    private static String bearer(Context ctx) {
        String header = ctx.header("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
