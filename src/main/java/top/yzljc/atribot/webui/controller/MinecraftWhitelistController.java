package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.official.minecraft.MinecraftWhitelist;
import top.yzljc.atribot.function.official.minecraft.MinecraftWhitelist.NameApplication;
import top.yzljc.atribot.webui.Result;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

public final class MinecraftWhitelistController {

    public static void list(Context ctx) {
        try {
            List<NameReviewItem> items = MinecraftWhitelist.list().stream()
                    .sorted(Comparator.comparing(NameApplication::appliedAt).reversed())
                    .map(MinecraftWhitelistController::toItem)
                    .toList();
            long pending = items.stream().filter(item -> "PENDING".equals(item.status())).count();
            long approved = items.size() - pending;
            ctx.json(Result.success(new NameReviewList(items, pending, approved, items.size())));
        } catch (RuntimeException e) {
            ctx.json(Result.fail(500, e.getMessage()));
        }
    }

    public static void submit(Context ctx) {
        NameApplicationRequest request = ctx.bodyAsClass(NameApplicationRequest.class);
        try {
            ctx.json(Result.success(toItem(MinecraftWhitelist.submit(request.getUserId(), request.getUsername()))));
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, e.getMessage()));
        } catch (RuntimeException e) {
            ctx.json(Result.fail(500, "保存玩家名申请失败: " + e.getMessage()));
        }
    }

    public static void approve(Context ctx) {
        try {
            ctx.json(Result.success(toItem(MinecraftWhitelist.approve(ctx.pathParam("username")))));
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(404, e.getMessage()));
        } catch (RuntimeException e) {
            ctx.json(Result.fail(500, "审核玩家名失败: " + e.getMessage()));
        }
    }

    public static void remove(Context ctx) {
        try {
            boolean removed = MinecraftWhitelist.remove(ctx.pathParam("username"));
            ctx.json(removed ? Result.success(null) : Result.fail(404, "玩家名申请不存在"));
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, e.getMessage()));
        } catch (RuntimeException e) {
            ctx.json(Result.fail(500, "移除玩家名申请失败: " + e.getMessage()));
        }
    }

    private static NameReviewItem toItem(NameApplication application) {
        return new NameReviewItem(
                application.userId(),
                application.username(),
                application.appliedAt(),
                application.approvedAt(),
                application.approvedAt() == null ? "PENDING" : "APPROVED",
                avatarUrl(application.username())
        );
    }

    private static String avatarUrl(String username) {
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        return ResourcesProperties.PLAYER_AVATAR_API.replace("{uuid}", encodedName);
    }

    @Data
    public static class NameApplicationRequest {
        private String userId;
        private String username;
    }

    public record NameReviewItem(
            String userId,
            String username,
            String appliedAt,
            String approvedAt,
            String status,
            String avatarUrl
    ) {
    }

    public record NameReviewList(List<NameReviewItem> items, long pending, long approved, long all) {
    }
}
