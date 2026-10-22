package top.yzljc.atribot.webui.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import top.yzljc.atribot.database.repo.BackgroundRepository;
import top.yzljc.atribot.function.background.BackgroundClient;
import top.yzljc.atribot.function.background.BackgroundService;
import top.yzljc.atribot.webui.Result;

/**
 * @Author YZ_Ljc_
 * @ClassName BackgroundController
 * @Created_at 2026/10/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
@Slf4j
public final class BackgroundController {
    private BackgroundController() {}

    public static void list(Context ctx) {
        execute(ctx, () -> {
            int page = integer(ctx.queryParam("page"), 1, 1_000_000);
            int size = integer(ctx.queryParam("pageSize"), 24, 100);
            String status = ctx.queryParam("status");
            if (!Set.of("PENDING", "REVIEWED", "DENIED").contains(status == null ? "" : status)) status = null;
            var counts = BackgroundRepository.counts();
            var rows = BackgroundRepository.list(status, page, size);
            List<Map<String, Object>> items = rows.stream().map(BackgroundController::item).toList();
            return Map.of("items", items, "total", counts.get(status == null ? "all" : status.toLowerCase(java.util.Locale.ROOT)),
                    "page", page, "pageSize", size);
        });
    }

    public static void count(Context ctx) { execute(ctx, BackgroundRepository::counts); }

    public record Review(String id, String status, String remark) {}
    public record Batch(List<String> ids, String status, String remark) {}

    public static void review(Context ctx) {
        execute(ctx, () -> {
            Review input = ctx.bodyAsClass(Review.class);
            String[] key = key(input.id());
            validate(input.status(), input.remark());
            BackgroundService.review(key[0], key[1], input.status(), input.remark());
            return "ok";
        });
    }

    public static void reviewBatch(Context ctx) {
        execute(ctx, () -> {
            Batch input = ctx.bodyAsClass(Batch.class);
            validate(input.status(), input.remark());
            if (input.ids() == null || input.ids().isEmpty() || input.ids().size() > 100)
                throw new IllegalArgumentException("每次审核 1 至 100 张图片");
            int ok = 0;
            for (String id : input.ids()) {
                String[] key = key(id);
                try { BackgroundService.review(key[0], key[1], input.status(), input.remark()); ok++; }
                catch (IllegalArgumentException e) { log.info("跳过已变更的背景申请: id={}", id); }
            }
            return Map.of("success", ok, "total", input.ids().size());
        });
    }

    public static void delete(Context ctx) {
        execute(ctx, () -> {
            Review input = ctx.bodyAsClass(Review.class);
            String[] key = key(input.id());
            BackgroundService.delete(key[0], key[1]);
            return "ok";
        });
    }

    public static void image(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        try {
            String userId = ctx.pathParam("userId"), id = ctx.pathParam("id");
            key(userId + "~" + id);
            if (!BackgroundRepository.get(userId).retainedIds().contains(id)) { ctx.status(404); return; }
            byte[] image = BackgroundClient.image(userId, id, !"false".equals(ctx.queryParam("thumbnail")));
            ctx.header("X-Content-Type-Options", "nosniff")
                    .contentType("image/jpeg").result(image);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ctx.status(503);
        } catch (Exception e) {
            log.warn("背景审核预览失败", e);
            ctx.status(502);
        }
    }

    private static Map<String, Object> item(BackgroundRepository.Entry entry) {
        var s = entry.settings().submission();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entry.userId() + "~" + s.id());
        item.put("imageUuid", s.id()); item.put("uploaderId", entry.userId());
        item.put("uploaderName", s.uploaderName()); item.put("groupId", s.groupId());
        String image = "/webui/api/backgrounds/" + entry.userId() + "/" + s.id() + "/image";
        item.put("displayUrl", image); item.put("fullUrl", image + "?thumbnail=false");
        item.put("width", s.width()); item.put("height", s.height()); item.put("fileSize", s.size());
        item.put("reviewStatus", s.status()); item.put("reviewer", s.reviewer()); item.put("reviewRemark", s.remark());
        item.put("createTime", s.createdAt()); item.put("reviewTime", s.reviewedAt() == 0 ? null : s.reviewedAt());
        return item;
    }

    private static void validate(String status, String remark) {
        if (status == null || !Set.of("PENDING", "REVIEWED", "DENIED").contains(status))
            throw new IllegalArgumentException("审核状态无效");
        if (remark != null && remark.length() > 500) throw new IllegalArgumentException("审核说明不能超过 500 字");
    }

    private static String[] key(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}~[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
            throw new IllegalArgumentException("背景申请标识无效");
        return value.split("~", 2);
    }

    private static int integer(String value, int fallback, int max) {
        try { return Math.max(1, Math.min(max, Integer.parseInt(value))); }
        catch (Exception e) { return fallback; }
    }

    @FunctionalInterface
    private interface Action { Object run() throws Exception; }

    private static void execute(Context ctx, Action action) {
        try { ctx.json(Result.success(action.run())); }
        catch (IllegalArgumentException e) { ctx.json(Result.customFailure(e.getMessage())); }
        catch (Exception e) {
            log.error("背景审核操作失败", e);
            ctx.json(Result.customFailure("背景操作失败，请稍后重试"));
        }
    }
}
