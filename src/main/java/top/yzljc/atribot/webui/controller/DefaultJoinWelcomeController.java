package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.chat.official.welcome.DefaultJoinWelcomeStore;
import top.yzljc.atribot.webui.Result;

import java.io.IOException;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName DefaultJoinWelcomeController
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
@Slf4j
public final class DefaultJoinWelcomeController {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void get(Context ctx) {
        UnifiedRole role = role(ctx);
        if (role == null) return;
        try {
            ctx.json(Result.success(Map.of("config", DefaultJoinWelcomeStore.getInstance().get(role))));
        } catch (IOException e) {
            failed(ctx, e);
        }
    }

    public static void save(Context ctx) {
        UnifiedRole role = role(ctx);
        if (role == null) return;
        if (ctx.body().length() > 131072) {
            ctx.status(413).json(Result.fail(413, "配置不能超过 128 Ki 字符"));
            return;
        }
        JsonNode config;
        try {
            config = MAPPER.readTree(ctx.body());
        } catch (IOException e) {
            ctx.status(400).json(Result.fail(400, "JSON 格式错误"));
            return;
        }
        try {
            ctx.json(Result.success(DefaultJoinWelcomeStore.getInstance().save(role, config)));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Result.fail(400, e.getMessage()));
        } catch (IOException e) {
            failed(ctx, e);
        }
    }

    public static void reset(Context ctx) {
        UnifiedRole role = role(ctx);
        if (role == null) return;
        try {
            ctx.json(Result.success(DefaultJoinWelcomeStore.getInstance().reset(role)));
        } catch (IOException e) {
            failed(ctx, e);
        }
    }

    private static UnifiedRole role(Context ctx) {
        try {
            return UnifiedRole.valueOf(ctx.pathParam("role"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Result.fail(400, "身份必须为 USER、ADMIN 或 OWNER"));
            return null;
        }
    }

    private static void failed(Context ctx, IOException e) {
        log.error("读写默认加群欢迎配置失败", e);
        ctx.status(500).json(Result.fail(500, "默认欢迎配置读写失败，请检查配置文件和目录权限"));
    }
}
