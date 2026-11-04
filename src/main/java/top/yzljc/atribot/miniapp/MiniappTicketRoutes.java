package top.yzljc.atribot.miniapp;

import io.javalin.Javalin;
import io.javalin.http.Context;
import top.yzljc.atribot.miniapp.service.MiniappTicketService;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappTicketRoutes
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
final class MiniappTicketRoutes {
    private static final String ROOT = "/atrimeow/profile/api/tickets";

    private MiniappTicketRoutes() {
    }

    private record Submission(String content) {
    }

    static void register(Javalin app, MiniappSessions sessions) {
        register(app, sessions, new MiniappTicketService());
    }

    static void register(Javalin app, MiniappSessions sessions, MiniappTicketService service) {
        app.get(ROOT, ctx -> handle(ctx, sessions, identity -> service.list(identity.userId(),
                (int) number(ctx.queryParam("offset"), 0, 0, 100000))));
        app.get(ROOT + "/{id}", ctx -> handle(ctx, sessions, identity -> service.detail(identity.userId(), id(ctx),
                number(ctx.queryParam("before"), Long.MAX_VALUE, 1, Long.MAX_VALUE))));
        app.post(ROOT, ctx -> handle(ctx, sessions, identity -> service.submit(identity, content(ctx), null)));
        app.post(ROOT + "/{id}/append", ctx -> handle(ctx, sessions,
                identity -> service.submit(identity, content(ctx), id(ctx))));
        app.post(ROOT + "/{id}/close", ctx -> handle(ctx, sessions, identity -> {
            var result = service.close(identity, id(ctx));
            return switch (result) {
                case CLOSED, ALREADY_CLOSED -> Map.of("closed", true);
                case NOT_FOUND -> throw new io.javalin.http.NotFoundResponse("TICKET_UNAVAILABLE");
                case FAILED -> throw new SQLException("关闭工单失败");
            };
        }));
    }

    private static String id(Context ctx) {
        String id = ctx.pathParam("id");
        if (!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("INVALID_TICKET_ID");
        return id;
    }

    private static String content(Context ctx) {
        if (ctx.contentType() == null || !ctx.contentType().toLowerCase(Locale.ROOT).startsWith("application/json")
                || ctx.bodyAsBytes().length > 32768) throw new IllegalArgumentException("INVALID_TICKET_CONTENT");
        try {
            var body = ctx.bodyAsClass(Submission.class);
            if (body == null) throw new IllegalArgumentException();
            return body.content();
        } catch (Exception e) {
            throw new IllegalArgumentException("INVALID_TICKET_CONTENT");
        }
    }

    private static long number(String value, long fallback, long min, long max) {
        if (value == null) return fallback;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("INVALID_TICKET_PAGE");
        }
    }

    private static void handle(Context ctx, MiniappSessions sessions, Operation operation) {
        var identity = MiniappRouter.requireIdentity(ctx, sessions);
        try {
            Object result = operation.apply(identity);
            if (result == null) {
                ctx.status(ctx.method().name().equals("GET") ? 404 : 502).json(Map.of("error", "TICKET_UNAVAILABLE"));
                return;
            }
            ctx.json(result);
        } catch (IllegalArgumentException e) {
            String code = "TICKET_NOT_OPEN".equals(e.getMessage()) ? "TICKET_NOT_OPEN" : "INVALID_TICKET_REQUEST";
            ctx.status(code.equals("TICKET_NOT_OPEN") ? 409 : 400).json(Map.of("error", code));
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(MiniappTicketRoutes.class).warn("个人中心工单操作失败", e);
            ctx.status(502).json(Map.of("error", "TICKET_UNAVAILABLE"));
        }
    }

    @FunctionalInterface
    private interface Operation {
        Object apply(MiniappSessions.Identity identity) throws SQLException;
    }
}
