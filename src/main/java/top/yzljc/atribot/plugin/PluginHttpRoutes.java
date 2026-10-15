package top.yzljc.atribot.plugin;

import io.javalin.Javalin;
import io.javalin.http.Handler;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName PluginHttpRoutes
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 */
public final class PluginHttpRoutes {
    private static final ConcurrentHashMap<String, Handler> POST_HANDLERS = new ConcurrentHashMap<>();

    private PluginHttpRoutes() {}

    public static void attach(Javalin server) {
        server.post("/plugins/{plugin}/*", ctx -> {
            Handler handler = POST_HANDLERS.get(ctx.path());
            if (handler == null) ctx.status(404);
            else handler.handle(ctx);
        });
    }

    static AutoCloseable registerPost(String owner, String path, Handler handler) {
        if (path == null || !path.matches("/[a-zA-Z0-9/_-]+"))
            throw new IllegalArgumentException("插件路径须以 / 开头，仅允许字母、数字、下划线、连字符和斜杠");
        String route = "/plugins/" + owner + path;
        if (POST_HANDLERS.putIfAbsent(route, handler) != null)
            throw new IllegalArgumentException("插件 HTTP 路径已注册: " + route);
        return () -> POST_HANDLERS.remove(route, handler);
    }
}
