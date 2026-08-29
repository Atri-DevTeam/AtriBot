package top.yzljc.atribot.webui.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
public class PublicOfficialQueryRepo {
    private static final DateTimeFormatter SQL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static long countGroupMessages(boolean botSent, LocalDateTime start, LocalDateTime end, String groupOpenId) {
        ChatStatsSnapshotRepo.ensureInitialized();
        return ChatStatsSnapshotRepo.countGroupMessages(botSent, start, end, groupOpenId);
    }

    public static long countC2CMessages(boolean botSent, LocalDateTime start, LocalDateTime end, String userOpenId) {
        ChatStatsSnapshotRepo.ensureInitialized();
        return ChatStatsSnapshotRepo.countC2CMessages(botSent, start, end, userOpenId);
    }

    public static DauStats queryDau(LocalDateTime start, LocalDateTime end, String groupOpenId, String userOpenId) {
        ChatStatsSnapshotRepo.DauStats stats = ChatStatsSnapshotRepo.queryDau(start, end, groupOpenId, userOpenId);
        return new DauStats(stats.groupReceiveUsers(), stats.groupSendGroups(), stats.c2cReceiveUsers(),
                stats.c2cSendUsers(), stats.totalReceiveUsers());
    }

    public static double queryAverageDailyDau() {
        return ChatStatsSnapshotRepo.averageDailyDau();
    }

    public static UserMessageStats queryUserMessageStats(String userOpenId) {
        ChatStatsSnapshotRepo.UserSummary summary = ChatStatsSnapshotRepo.userSummary(userOpenId);
        return new UserMessageStats(summary.c2cReceived(), summary.c2cSent(), summary.groupReceived(),
                summary.firstSeenAt(), summary.lastSeenAt(), summary.lastUsername());
    }

    public static GroupMessageStats queryGroupMessageStats(String groupOpenId) {
        ChatStatsSnapshotRepo.GroupSummary summary = ChatStatsSnapshotRepo.groupSummary(groupOpenId);
        return new GroupMessageStats(summary.received(), summary.sent(), summary.activeUsers(),
                summary.firstSeenAt(), summary.lastSeenAt());
    }

    /**
     * 按天聚合消息量与 DAU，供统计页画折线图
     *
     * @param start 起始时间，闭区间
     * @param end   结束时间，开区间
     * @return 按日期升序排列的数据点，仅包含有记录的日期
     */
    public static List<DailyPoint> queryDailySeries(LocalDateTime start, LocalDateTime end) {
        return ChatStatsSnapshotRepo.dailySeries(start, end).stream()
                .map(point -> new DailyPoint(point.date(), point.groupReceived(), point.groupSent(),
                        point.c2cReceived(), point.c2cSent(), point.dau()))
                .toList();
    }

    private static long queryLong(String sql, StatementBinder binder) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("公开统计查询失败: {}", e.getMessage(), e);
        }
        return 0;
    }

    private static void appendBotSendPredicate(StringBuilder sql, boolean botSent) {
        sql.append(botSent ? "= ?" : "<> ?");
    }

    private static void appendTimeRange(StringBuilder sql, LocalDateTime start, LocalDateTime end, String timeExpression) {
        if (start != null) {
            sql.append(" AND ").append(timeExpression).append(" >= ?");
        }
        if (end != null) {
            sql.append(" AND ").append(timeExpression).append(" < ?");
        }
    }

    private static int bindTimeRange(PreparedStatement ps, int idx, LocalDateTime start, LocalDateTime end) throws SQLException {
        if (start != null) {
            ps.setString(idx++, toSqlTime(start));
        }
        if (end != null) {
            ps.setString(idx++, toSqlTime(end));
        }
        return idx;
    }

    private static String toSqlTime(LocalDateTime value) {
        return value.format(SQL_TIME_FORMATTER);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private interface StatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    public record DailyPoint(String date, long groupReceived, long groupSent,
                             long c2cReceived, long c2cSent, long dau) {
    }

    public record DauStats(long groupReceiveUsers, long groupSendGroups, long c2cReceiveUsers,
                           long c2cSendUsers, long totalReceiveUsers) {
    }

    public record UserMessageStats(long c2cReceivedMessages, long c2cSentMessages,
                                   long groupReceivedMessages, String firstSeenAt,
                                   String lastSeenAt, String lastUsername) {
    }

    public record GroupMessageStats(long receivedMessages, long sentMessages, long activeUsers,
                                    String firstSeenAt, String lastSeenAt) {
    }
}
