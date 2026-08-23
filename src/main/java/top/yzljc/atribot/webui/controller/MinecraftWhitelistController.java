package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.service.minecraft.MinecraftModerationClient;
import top.yzljc.atribot.service.minecraft.MinecraftModerationClient.ModerationException;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;
import top.yzljc.atribot.webui.Result;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minecraft 远端名字与皮肤审核的 WebUI 代理。 */
public final class MinecraftWhitelistController {
    private MinecraftWhitelistController() {}

    public static void submitPlayer(Context ctx) {
        run(ctx, () -> {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            String player = body == null ? null : body.path("player").asText(null);
            if (player == null || player.isBlank()) throw new IllegalArgumentException("请输入正版玩家名或 UUID");
            var profile = FetchMinecraftProfile.find(player.trim());
            if (profile == null) throw new IllegalArgumentException("无法找到该正版 Minecraft 玩家");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("profile", Map.of("name", profile.username(), "uuid", profile.uuid().toString()));
            data.put("name", MinecraftModerationClient.submitName(profile.username()));
            data.put("skin", MinecraftModerationClient.submitSkin(player.trim()));
            ctx.json(Result.success(data));
        });
    }

    public static void listNames(Context ctx) {
        run(ctx, () -> ctx.json(Result.success(MinecraftModerationClient.listNames(
                ctx.queryParam("status"), intParam(ctx, "page", 1), intParam(ctx, "size", 20)))));
    }

    public static void listSkins(Context ctx) {
        run(ctx, () -> ctx.json(Result.success(MinecraftModerationClient.listSkins(
                ctx.queryParam("status"), intParam(ctx, "page", 1), intParam(ctx, "size", 20)))));
    }

    public static void reviewName(Context ctx) { review(ctx, true); }
    public static void reviewSkin(Context ctx) { review(ctx, false); }

    private static void review(Context ctx, boolean name) {
        run(ctx, () -> {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            String status = body == null ? null : body.path("status").asText(null);
            if (!"APPROVED".equals(status) && !"BLACKLISTED".equals(status) && !"PENDING".equals(status))
                throw new IllegalArgumentException("审核状态无效");
            long id = Long.parseLong(ctx.pathParam("id"));
            String reason = body.path("reason").asText("");
            String reviewer = body.path("reviewer").asText("webui");
            JsonNode data = name ? MinecraftModerationClient.reviewName(id, status, reason, reviewer)
                    : MinecraftModerationClient.reviewSkin(id, status, reason, reviewer);
            ctx.json(Result.success(data));
        });
    }

    public static void skinPreview(Context ctx) {
        run(ctx, () -> {
            String type = ctx.pathParam("type").toUpperCase();
            if (!type.equals("AVATAR") && !type.equals("SKIN3D") && !type.equals("SKIN")) throw new IllegalArgumentException("预览类型无效");
            ctx.contentType("image/png").result(MinecraftModerationClient.preview(ctx.pathParam("skinId"), type));
        });
    }

    private static int intParam(Context ctx, String name, int fallback) {
        try { return Integer.parseInt(ctx.queryParam(name) == null ? String.valueOf(fallback) : ctx.queryParam(name)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static void run(Context ctx, Action action) {
        try { action.run(); }
        catch (IllegalArgumentException e) { ctx.status(400).json(Result.fail(400, e.getMessage())); }
        catch (ModerationException e) {
            int status = e.status() >= 400 && e.status() <= 599 ? e.status() : 502;
            ctx.status(status).json(Result.fail(status, e.getMessage()));
        }
        catch (Exception e) { ctx.status(500).json(Result.fail(500, "Minecraft 审核操作失败: " + e.getMessage())); }
    }

    private interface Action { void run() throws Exception; }
}
