package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import top.yzljc.atribot.chat.official.moderation.GroupModerationSettings;
import top.yzljc.atribot.chat.official.moderation.GroupModerationStore;
import top.yzljc.atribot.database.repo.ModerationLogRepository;
import top.yzljc.atribot.webui.Result;

import java.util.List;

import static top.yzljc.atribot.webui.WebUiSupport.formatFeedbackTime;
import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseInt;

/**
* @Author AndyOctopus
* @ClassName GroupModerationController
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.webui.controller
*/
public final class GroupModerationController {

    public static void getSettings(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        ctx.json(Result.success(GroupModerationStore.get(groupOpenId)));
    }

    public static void saveSettings(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        GroupModerationSettings settings = ctx.bodyAsClass(GroupModerationSettings.class);
        GroupModerationStore.save(groupOpenId, settings);
        ctx.json(Result.success(settings));
    }

    public static void listLogs(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 30);
        if (pageSize > 200) {
            pageSize = 200;
        }
        String category = normalizeCategory(ctx.queryParam("category"));
        String keyword = ctx.queryParam("keyword");

        int total = ModerationLogRepository.count(groupOpenId, category, keyword);
        List<ModerationLogItemDTO> items = ModerationLogRepository.findPaginated(groupOpenId, page, pageSize, category, keyword)
                .stream()
                .map(row -> new ModerationLogItemDTO(
                        row.id(),
                        row.category(),
                        row.action(),
                        row.targetMemberOpenId(),
                        row.detail(),
                        formatFeedbackTime(row.createdAt())
                ))
                .toList();
        ctx.json(Result.success(new ModerationLogListResult(items, total, page, pageSize)));
    }

    public static void logStats(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        ModerationLogRepository.Stats stats = ModerationLogRepository.stats(groupOpenId);
        ctx.json(Result.success(new ModerationLogStatsDTO(
                stats.all(),
                stats.today(),
                stats.last24h(),
                stats.keywordRecall(),
                stats.aiRecall(),
                stats.joinReview()
        )));
    }

    private static String normalizeCategory(String raw) {
        if (isBlank(raw) || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        String category = raw.trim().toUpperCase();
        return switch (category) {
            case ModerationLogRepository.CATEGORY_KEYWORD_RECALL,
                 ModerationLogRepository.CATEGORY_AI_RECALL,
                 ModerationLogRepository.CATEGORY_JOIN_REVIEW -> category;
            default -> null;
        };
    }

    public record ModerationLogItemDTO(long id, String category, String action, String targetMemberOpenId,
                                       String detail, String createdAt) {
    }

    public record ModerationLogListResult(List<ModerationLogItemDTO> items, int total, int page, int pageSize) {
    }

    public record ModerationLogStatsDTO(int all, int today, int last24h, int keywordRecall, int aiRecall,
                                        int joinReview) {
    }
}
