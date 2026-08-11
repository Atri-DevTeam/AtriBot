package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.repo.PublicOfficialQueryRepo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static top.yzljc.atribot.webui.WebUiSupport.firstNonBlank;
import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.nullToDash;
import static top.yzljc.atribot.webui.WebUiSupport.trimToNull;

/** 公开官方机器人查询 */
public class PublicQueryController {

    private static final DateTimeFormatter PUBLIC_QUERY_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long PUBLIC_QUERY_CACHE_TTL_MILLIS = 60_000L;
    private static final ConcurrentHashMap<String, PublicQueryCacheEntry> PUBLIC_QUERY_CACHE = new ConcurrentHashMap<>();

    public static void publicOfficialGroupReceivedMessages(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String groupOpenId = trimToNull(ctx.queryParam("groupOpenId"));
        String cacheKey = publicCacheKey("group_received", window, groupOpenId, null);
        publicAsyncCached(ctx, cacheKey, () -> new PublicMessageCountDTO(
                "official_group_received_messages",
                "group",
                window.startString(),
                window.endString(),
                groupOpenId,
                null,
                PublicOfficialQueryRepo.countGroupMessages(false, window.start(), window.end(), groupOpenId)
        ));
    }

    public static void publicOfficialGroupSentMessages(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String groupOpenId = trimToNull(ctx.queryParam("groupOpenId"));
        String cacheKey = publicCacheKey("group_sent", window, groupOpenId, null);
        publicAsyncCached(ctx, cacheKey, () -> new PublicMessageCountDTO(
                "official_group_sent_messages",
                "group",
                window.startString(),
                window.endString(),
                groupOpenId,
                null,
                PublicOfficialQueryRepo.countGroupMessages(true, window.start(), window.end(), groupOpenId)
        ));
    }

    public static void publicOfficialC2CReceivedMessages(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String userOpenId = trimToNull(firstNonBlank(ctx.queryParam("userOpenId"), ctx.queryParam("unionOpenId")));
        String cacheKey = publicCacheKey("c2c_received", window, null, userOpenId);
        publicAsyncCached(ctx, cacheKey, () -> new PublicMessageCountDTO(
                "official_c2c_received_messages",
                "c2c",
                window.startString(),
                window.endString(),
                null,
                userOpenId,
                PublicOfficialQueryRepo.countC2CMessages(false, window.start(), window.end(), userOpenId)
        ));
    }

    public static void publicOfficialC2CSentMessages(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String userOpenId = trimToNull(firstNonBlank(ctx.queryParam("userOpenId"), ctx.queryParam("unionOpenId")));
        String cacheKey = publicCacheKey("c2c_sent", window, null, userOpenId);
        publicAsyncCached(ctx, cacheKey, () -> new PublicMessageCountDTO(
                "official_c2c_sent_messages",
                "c2c",
                window.startString(),
                window.endString(),
                null,
                userOpenId,
                PublicOfficialQueryRepo.countC2CMessages(true, window.start(), window.end(), userOpenId)
        ));
    }

    public static void publicOfficialDau(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String groupOpenId = trimToNull(ctx.queryParam("groupOpenId"));
        String userOpenId = trimToNull(firstNonBlank(ctx.queryParam("userOpenId"), ctx.queryParam("unionOpenId")));
        String cacheKey = publicCacheKey("dau", window, groupOpenId, userOpenId);
        publicAsyncCached(ctx, cacheKey, () -> {
            var stats = PublicOfficialQueryRepo.queryDau(window.start(), window.end(), groupOpenId, userOpenId);
            double totalDau = PublicOfficialQueryRepo.queryAverageDailyDau();
            long groupReceiveMessages = PublicOfficialQueryRepo.countGroupMessages(false, window.start(), window.end(), groupOpenId);
            long groupSendMessages = PublicOfficialQueryRepo.countGroupMessages(true, window.start(), window.end(), groupOpenId);
            long c2cReceiveMessages = PublicOfficialQueryRepo.countC2CMessages(false, window.start(), window.end(), userOpenId);
            long c2cSendMessages = PublicOfficialQueryRepo.countC2CMessages(true, window.start(), window.end(), userOpenId);
            return new PublicDauDTO(
                    window.startString(),
                    window.endString(),
                    groupOpenId,
                    userOpenId,
                    stats.totalReceiveUsers(),
                    totalDau,
                    groupReceiveMessages,
                    groupSendMessages,
                    c2cReceiveMessages,
                    c2cSendMessages
            );
        });
    }

    /**
     * 按日聚合的消息量与 DAU 序列，供统计页画折线图。
     * 单点接口只能给出区间总量，画不出趋势。
     */
    public static void publicOfficialSeries(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String cacheKey = publicCacheKey("series", window, null, null);
        publicAsyncCached(ctx, cacheKey, () -> new PublicSeriesDTO(
                window.startString(),
                window.endString(),
                PublicOfficialQueryRepo.queryDailySeries(window.start(), window.end())
        ));
    }

    public record PublicSeriesDTO(String startTime, String endTime,
                                  List<PublicOfficialQueryRepo.DailyPoint> points) {}

    public static void publicOfficialUserInfo(Context ctx) {
        String userOpenId = trimToNull(firstNonBlank(ctx.pathParam("userOpenId"), ctx.queryParam("userOpenId"), ctx.queryParam("unionOpenId")));
        if (userOpenId == null) {
            ctx.json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        String cacheKey = publicCacheKey("user_info", null, null, userOpenId);
        publicAsyncCached(ctx, cacheKey, () -> {
            var user = OfficialUsers.getData(userOpenId);
            var stats = PublicOfficialQueryRepo.queryUserMessageStats(userOpenId);
            return new PublicUserInfoDTO(
                    user.userOpenId(),
                    user.role().name(),
                    user.permissions(),
                    user.isBlocked(),
                    user.isIgnored(),
                    user.c2cPush(),
                    stats.c2cReceivedMessages(),
                    stats.c2cSentMessages(),
                    stats.groupReceivedMessages(),
                    stats.firstSeenAt(),
                    stats.lastSeenAt(),
                    stats.lastUsername()
            );
        });
    }

    public static void publicOfficialGroupInfo(Context ctx) {
        String groupOpenId = trimToNull(firstNonBlank(ctx.pathParam("groupOpenId"), ctx.queryParam("groupOpenId")));
        if (groupOpenId == null) {
            ctx.json(Result.fail(400, "groupOpenId 不能为空"));
            return;
        }
        String cacheKey = publicCacheKey("group_info", null, groupOpenId, null);
        publicAsyncCached(ctx, cacheKey, () -> {
            var group = OfficialGroups.getData(groupOpenId);
            var stats = PublicOfficialQueryRepo.queryGroupMessageStats(groupOpenId);
            return new PublicGroupInfoDTO(
                    group.groupOpenId(),
                    group.opMemberOpenId(),
                    group.joinedAt(),
                    group.isWhitelist(),
                    group.isBlacklisted(),
                    group.allowProactiveMsg(),
                    group.realGroupId(),
                    group.memberOpenid(),
                    group.recvMsgSetting() == null ? null : group.recvMsgSetting().getJsonValue(),
                    group.memberRole() == null ? null : group.memberRole().name(),
                    group.groupName(),
                    group.groupFingerMemo(),
                    group.groupClassText(),
                    group.groupTags(),
                    group.groupMemberNum(),
                    stats.receivedMessages(),
                    stats.sentMessages(),
                    stats.activeUsers(),
                    stats.firstSeenAt(),
                    stats.lastSeenAt()
            );
        });
    }

    private static <T> void publicAsyncCached(Context ctx, String cacheKey, Supplier<T> supplier) {
        PublicQueryCacheEntry cached = PUBLIC_QUERY_CACHE.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.createdAt() < PUBLIC_QUERY_CACHE_TTL_MILLIS) {
            ctx.json(cached.result());
            return;
        }

        publicAsync(ctx, () -> {
            T data = supplier.get();
            Result<T> result = Result.success(data);
            PUBLIC_QUERY_CACHE.put(cacheKey, new PublicQueryCacheEntry(now, result));
            cleanupPublicQueryCache(now);
            return result;
        }, true);
    }

    private static <T> void publicAsync(Context ctx, Supplier<T> supplier) {
        publicAsync(ctx, supplier, false);
    }

    private static <T> void publicAsync(Context ctx, Supplier<T> supplier, boolean alreadyWrapped) {
        ctx.future(() -> ThreadManager.supplyAsync(() -> {
            try {
                return alreadyWrapped ? supplier.get() : Result.success(supplier.get());
            } catch (IllegalArgumentException e) {
                return Result.fail(400, e.getMessage());
            } catch (Exception e) {
                return Result.fail(500, "公开查询失败: " + e.getMessage());
            }
        }).thenAccept(ctx::json));
    }

    private static String publicCacheKey(String name, QueryWindow window, String groupOpenId, String userOpenId) {
        String start = window == null ? "-" : String.valueOf(window.startString());
        String end = window == null ? "-" : String.valueOf(window.endString());
        return name + "|start=" + start + "|end=" + end + "|group=" + nullToDash(groupOpenId) + "|user=" + nullToDash(userOpenId);
    }

    private static void cleanupPublicQueryCache(long now) {
        if (PUBLIC_QUERY_CACHE.size() < 512) {
            return;
        }
        PUBLIC_QUERY_CACHE.entrySet().removeIf(entry -> now - entry.getValue().createdAt() >= PUBLIC_QUERY_CACHE_TTL_MILLIS);
    }

    private static QueryWindow parseQueryWindowOrFail(Context ctx) {
        try {
            return parseQueryWindow(ctx);
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, e.getMessage()));
            return null;
        }
    }

    private static QueryWindow parseQueryWindow(Context ctx) {
        if (Boolean.parseBoolean(ctx.queryParam("all"))) {
            return new QueryWindow(null, null);
        }

        String startValue = firstNonBlank(ctx.queryParam("start"), ctx.queryParam("startTime"), ctx.queryParam("from"));
        String endValue = firstNonBlank(ctx.queryParam("end"), ctx.queryParam("endTime"), ctx.queryParam("to"));
        LocalDateTime start;
        LocalDateTime end;

        if (isBlank(startValue) && isBlank(endValue)) {
            start = LocalDate.now(BEIJING_ZONE).atStartOfDay();
            end = start.plusDays(1);
        } else if (isBlank(startValue)) {
            end = parsePublicQueryTime(endValue, true);
            start = end.minusDays(1);
        } else if (isBlank(endValue)) {
            start = parsePublicQueryTime(startValue, false);
            end = start.plusDays(1);
        } else {
            start = parsePublicQueryTime(startValue, false);
            end = parsePublicQueryTime(endValue, true);
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end 必须晚于 start");
        }
        return new QueryWindow(start, end);
    }

    private static LocalDateTime parsePublicQueryTime(String value, boolean endOfDate) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("时间参数不能为空");
        }

        String normalized = value.trim();
        try {
            if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(normalized);
                return endOfDate ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
            }
            if (normalized.indexOf(' ') > 0) {
                return LocalDateTime.parse(normalized, PUBLIC_QUERY_TIME_FMT);
            }
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式无效，支持 yyyy-MM-dd、yyyy-MM-dd HH:mm:ss 或 ISO LocalDateTime");
        }
    }

    private record QueryWindow(LocalDateTime start, LocalDateTime end) {
        String startString() {
            return start == null ? null : start.format(PUBLIC_QUERY_TIME_FMT);
        }

        String endString() {
            return end == null ? null : end.format(PUBLIC_QUERY_TIME_FMT);
        }
    }

    private record PublicQueryCacheEntry(long createdAt, Result<?> result) {
    }

    public record PublicMessageCountDTO(String metric, String scope, String startTime, String endTime,
                                        String groupOpenId, String userOpenId, long count) {
    }

    public record PublicDauDTO(String startTime, String endTime, String groupOpenId, String userOpenId,
                               long dau, double totalDau,
                               long groupReceiveMessages, long groupSendMessages,
                               long c2cReceiveMessages, long c2cSendMessages) {
    }

    public record PublicUserInfoDTO(String userOpenId, String role, java.util.Set<String> permissions,
                                    boolean isBlocked, boolean isIgnored, boolean c2cPush,
                                    long c2cReceivedMessages, long c2cSentMessages,
                                    long groupReceivedMessages, String firstSeenAt,
                                    String lastSeenAt, String lastUsername) {
    }

    public record PublicGroupInfoDTO(String groupOpenId, String opMemberOpenId, String joinedAt,
                                     boolean whitelist, boolean blacklisted, boolean allowProactiveMsg,
                                     Long realGroupId, String memberOpenid, String recvMsgSetting,
                                     String memberRole, String groupName, String groupFingerMemo,
                                     String groupClassText, List<String> groupTags, int groupMemberNum,
                                     long receivedMessages, long sentMessages,
                                     long activeUsers, String firstSeenAt, String lastSeenAt) {
    }
}
