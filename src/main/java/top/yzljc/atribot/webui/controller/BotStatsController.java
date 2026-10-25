package top.yzljc.atribot.webui.controller;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.ApiCode;
import top.yzljc.atribot.webui.repo.BotStatsRepository;
import top.yzljc.atribot.webui.repo.BotStatsCache;
import top.yzljc.atribot.webui.repo.BotStatsSection;
import top.yzljc.atribot.webui.repo.PublicOfficialQueryRepo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @Author YZ_Ljc_
 * @ClassName BotStatsController
 * @Created_at 2026/10/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
@Slf4j
public final class BotStatsController {
    private static final BotStatsController INSTANCE = new BotStatsController(BotStatsCache::snapshot, BotStatsController::messages);
    private final Cache<String, Object> cache = CacheBuilder.newBuilder()
            .maximumSize(1).expireAfterWrite(Duration.ofSeconds(60)).build();
    private final Supplier<BotStatsRepository.Snapshot> loader;
    private final Supplier<Object> messageQuery;

    BotStatsController(Supplier<BotStatsRepository.Snapshot> loader, Supplier<Object> messageQuery) {
        this.loader = loader;
        this.messageQuery = messageQuery;
    }

    public static void query(Context ctx) {
        INSTANCE.handle(ctx);
    }

    void handle(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        if (!ctx.queryParamMap().isEmpty()) {
            ctx.status(400).json(Result.customFailure("此接口不接受查询参数，请直接请求 /openapi/bot_stats"));
            return;
        }
        ctx.future(() -> ThreadManager.supplyAsync(() -> {
            try {
                return Result.success(response(loader.get()));
            } catch (Exception e) {
                log.error("聚合统计查询失败", e);
                return Result.failure(ApiCode.STATISTICS_QUERY_FAILED);
            }
        }).thenAccept(ctx::json));
    }

    private Map<String, Object> response(BotStatsRepository.Snapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        var unavailable = new ArrayList<>(snapshot.unavailable());
        result.put("schemaVersion", 1);
        result.put("generatedAt", snapshot.generatedAt());
        result.put("cacheSeconds", 60);
        result.put("leaderboardLimit", BotStatsSection.LEADERBOARD_LIMIT);
        result.put("registrations", snapshot.registrations());
        try {
            result.put("messages", cache.get("messages", messageQuery::get));
        } catch (Exception e) {
            log.warn("聚合消息统计读取失败", e);
            unavailable.add("messages");
            result.put("messages", null);
        }
        Map<String, Object> games = new LinkedHashMap<>();
        if (snapshot.games() != null) snapshot.games().forEach((game, section) -> games.put(game, section.view()));
        result.put("games", snapshot.games() == null ? null : games);
        result.put("loot", view(snapshot.loot()));
        result.put("sign", view(snapshot.sign()));
        result.put("unavailable", unavailable);
        return Collections.unmodifiableMap(result);
    }

    private static Object view(BotStatsSection section) {
        return section == null ? null : section.view();
    }

    private static Object messages() {
        var today = LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay();
        return Map.of("todayDate", today.toLocalDate().toString(),
                "total", messageCounts(null, null),
                "today", messageCounts(today, today.plusDays(1)),
                "averageDailyDau", PublicOfficialQueryRepo.queryAverageDailyDau());
    }

    private static Map<String, Object> messageCounts(LocalDateTime start, LocalDateTime end) {
        var dau = PublicOfficialQueryRepo.queryDau(start, end, null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupReceived", PublicOfficialQueryRepo.countGroupMessages(false, start, end, null));
        result.put("groupSent", PublicOfficialQueryRepo.countGroupMessages(true, start, end, null));
        result.put("c2cReceived", PublicOfficialQueryRepo.countC2CMessages(false, start, end, null));
        result.put("c2cSent", PublicOfficialQueryRepo.countC2CMessages(true, start, end, null));
        result.put("activeUsers", dau.totalReceiveUsers());
        result.put("groupActiveUsers", dau.groupReceiveUsers());
        result.put("c2cActiveUsers", dau.c2cReceiveUsers());
        result.put("sentGroups", dau.groupSendGroups());
        result.put("sentC2CUsers", dau.c2cSendUsers());
        return result;
    }
}
