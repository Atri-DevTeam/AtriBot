package top.yzljc.atribot.miniapp;

import io.javalin.Javalin;
import io.javalin.http.Context;
import top.yzljc.atribot.function.skyblockprice.SkyblockMarketClient;
import top.yzljc.atribot.function.skyblockprice.SkyblockPriceAlertService;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappPriceAlertRoutes
 * @Created_at 2026/10/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
final class MiniappPriceAlertRoutes {
    private static final String ROOT = "/atrimeow/profile/api/price-alerts";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private MiniappPriceAlertRoutes() {
    }

    static void register(Javalin app, MiniappSessions sessions) {
        var service = SkyblockPriceAlertService.INSTANCE;
        app.get(ROOT, ctx -> handle(ctx, sessions, service::load));
        app.get(ROOT + "/search", ctx -> handle(ctx, sessions,
                user -> service.search(user, ctx.queryParam("q"), ctx.queryParam("cursor"))));
        app.post(ROOT + "/item", ctx -> handle(ctx, sessions,
                user -> service.item(user, body(ctx, SkyblockMarketClient.Item.class))));
        app.post(ROOT + "/icon", ctx -> {
            MiniappRouter.requireIdentity(ctx, sessions);
            ctx.future(() -> CompletableFuture.runAsync(() -> handle(ctx, sessions, user -> {
                var result = service.icon(user, body(ctx, SkyblockMarketClient.Item.class));
                return Map.of("hash", result.path("hash").asText(), "width", 128, "height", 128,
                        "placeholder", result.path("placeholder").asBoolean(false),
                        "tier", result.path("tier").asText(""));
            }), ThreadManager.getExecutor()));
        });
        app.post(ROOT + "/save", ctx -> handle(ctx, sessions, user -> {
            service.save(user, body(ctx, SkyblockPriceAlertService.Save.class), () -> MiniappRouter.requireIdentity(ctx, sessions));
            return service.load(user);
        }));
        app.post(ROOT + "/action", ctx -> handle(ctx, sessions, user -> {
            service.operate(user, body(ctx, SkyblockPriceAlertService.Operation.class));
            return service.load(user);
        }));
        app.post(ROOT + "/test", ctx -> {
            var identity = MiniappRouter.requireIdentity(ctx, sessions);
            SkyblockPriceAlertService.TestRequest request;
            try {
                SkyblockPriceAlertService.requireTestPermission(identity.userId());
                request = body(ctx, SkyblockPriceAlertService.TestRequest.class);
            } catch (SkyblockPriceAlertService.Problem problem) {
                ctx.status(problem.status).json(Map.of("error", problem.code));
                return;
            }
            ctx.future(() -> CompletableFuture.runAsync(() -> handle(ctx, sessions, user -> {
                service.test(user, request, () -> MiniappRouter.requireIdentity(ctx, sessions));
                return Map.of("success", true);
            }), ThreadManager.getExecutor()));
        });
        app.post(ROOT + "/switch", ctx -> handle(ctx, sessions, user -> {
            var request = body(ctx, Switch.class);
            if (request.enabled() == null) throw new SkyblockPriceAlertService.Problem(400, "INVALID_PRICE_ALERT");
            service.setEnabled(user, request.enabled(), () -> MiniappRouter.requireIdentity(ctx, sessions));
            return service.load(user);
        }));
        app.get(ROOT + "/image/{hash}", ctx -> {
            var identity = MiniappRouter.requireIdentity(ctx, sessions);
            if (!SkyblockPriceAlertService.canUse(identity.userId())) {
                ctx.status(403).json(Map.of("error", "PRICE_PERMISSION_REQUIRED"));
                return;
            }
            String hash = ctx.pathParam("hash");
            if (!hash.matches("[0-9a-f]{64}")) throw new io.javalin.http.BadRequestResponse();
            ctx.future(() -> CompletableFuture.runAsync(() -> {
                try {
                    var request = HttpRequest.newBuilder(URI.create(OpenApi.get("bot.hypixel.skyblock.icon.get", "hash", hash)))
                            .timeout(Duration.ofSeconds(15)).GET().build();
                    var response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    byte[] bytes;
                    try (var input = response.body()) {
                        bytes = input.readNBytes(262145);
                    }
                    if (response.statusCode() != 200 || bytes.length == 0 || bytes.length > 262144
                            || !response.headers().firstValue("Content-Type").orElse("").startsWith("image/png"))
                        throw new IOException("物品图标响应无效");
                    MiniappRouter.requireIdentity(ctx, sessions);
                    if (!SkyblockPriceAlertService.canUse(identity.userId())) {
                        ctx.status(403).json(Map.of("error", "PRICE_PERMISSION_REQUIRED"));
                        return;
                    }
                    ctx.contentType("image/png").result(bytes);
                } catch (IOException | IllegalStateException e) {
                    ctx.status(502).json(Map.of("error", "PRICE_ICON_UNAVAILABLE"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    ctx.status(502).json(Map.of("error", "PRICE_ICON_UNAVAILABLE"));
                }
            }, ThreadManager.getExecutor()));
        });
    }

    private record Switch(Boolean enabled) {
    }

    private static <T> T body(Context ctx, Class<T> type) {
        if (ctx.contentType() == null || !ctx.contentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json")
                || ctx.bodyAsBytes().length > 8192)
            throw new SkyblockPriceAlertService.Problem(400, "INVALID_PRICE_ALERT");
        try {
            var result = ctx.bodyAsClass(type);
            if (result == null) throw new IllegalArgumentException();
            return result;
        } catch (Exception e) {
            throw new SkyblockPriceAlertService.Problem(400, "INVALID_PRICE_ALERT");
        }
    }

    private static void handle(Context ctx, MiniappSessions sessions, Operation operation) {
        var identity = MiniappRouter.requireIdentity(ctx, sessions);
        try {
            SkyblockPriceAlertService.requirePermission(identity.userId());
            Object result = operation.apply(identity.userId());
            MiniappRouter.requireIdentity(ctx, sessions);
            SkyblockPriceAlertService.requirePermission(identity.userId());
            ctx.json(result);
        } catch (SkyblockPriceAlertService.Problem problem) {
            ctx.status(problem.status).json(Map.of("error", problem.code));
        } catch (SQLException | IOException | IllegalStateException e) {
            org.slf4j.LoggerFactory.getLogger(MiniappPriceAlertRoutes.class)
                    .warn("价格提醒请求失败: type={}", e.getClass().getSimpleName());
            ctx.status(502).json(Map.of("error", "PRICE_ALERT_UNAVAILABLE"));
        }
    }

    @FunctionalInterface
    private interface Operation {
        Object apply(String userId) throws IOException, SQLException;
    }
}
