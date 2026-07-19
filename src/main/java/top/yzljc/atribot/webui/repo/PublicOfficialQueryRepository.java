package top.yzljc.atribot.webui.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class PublicOfficialQueryRepository {

    private static final String GROUP_TABLE = "official_group_record";
    private static final String C2C_TABLE = "official_c2c_record";
    private static final String BOT_SEND = "BOT_SEND";

    private PublicOfficialQueryRepository() {
    }

    public static long countGroupMessages(boolean botSent, LocalDateTime start, LocalDateTime end, String groupOpenId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM `")
                .append(GROUP_TABLE)
                .append("` WHERE event_type ");
        appendBotSendPredicate(sql, botSent);
        boolean hasGroup = !isBlank(groupOpenId);
        appendTimeRange(sql, start, end);
        if (hasGroup) {
            sql.append(" AND group_openId = ?");
        }

        return queryLong(sql.toString(), ps -> {
            int idx = 1;
            ps.setString(idx++, BOT_SEND);
            idx = bindTimeRange(ps, idx, start, end);
            if (hasGroup) {
                ps.setString(idx, groupOpenId);
            }
        });
    }

    public static long countC2CMessages(boolean botSent, LocalDateTime start, LocalDateTime end, String userOpenId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM `")
                .append(C2C_TABLE)
                .append("` WHERE source ");
        appendBotSendPredicate(sql, botSent);
        boolean hasUser = !isBlank(userOpenId);
        appendTimeRange(sql, start, end);
        if (hasUser) {
            sql.append(" AND union_openId = ?");
        }

        return queryLong(sql.toString(), ps -> {
            int idx = 1;
            ps.setString(idx++, BOT_SEND);
            idx = bindTimeRange(ps, idx, start, end);
            if (hasUser) {
                ps.setString(idx, userOpenId);
            }
        });
    }

    public static DauStats queryDau(LocalDateTime start, LocalDateTime end, String groupOpenId, String userOpenId) {
        long groupReceiveUsers = countDistinctGroupUsers(false, start, end, groupOpenId);
        long groupSendGroups = countDistinctGroupTargets(true, start, end, groupOpenId);
        long c2cReceiveUsers = countDistinctC2CUsers(false, start, end, userOpenId);
        long c2cSendUsers = countDistinctC2CUsers(true, start, end, userOpenId);
        long totalReceiveUsers = countDistinctReceiveUsers(start, end, groupOpenId, userOpenId);
        return new DauStats(groupReceiveUsers, groupSendGroups, c2cReceiveUsers, c2cSendUsers, totalReceiveUsers);
    }

    public static double queryAverageDailyDau() {
        String sql = "SELECT COALESCE(AVG(daily_dau), 0) FROM (" +
                "  SELECT active_date, COUNT(*) AS daily_dau FROM (" +
                "    SELECT DATE(created_at) AS active_date, union_openId AS user_id FROM `" + GROUP_TABLE + "` " +
                "    WHERE event_type <> ? AND union_openId IS NOT NULL " +
                "    UNION " +
                "    SELECT DATE(created_at) AS active_date, union_openId AS user_id FROM `" + C2C_TABLE + "` " +
                "    WHERE source <> ? AND union_openId IS NOT NULL" +
                "  ) daily_users GROUP BY active_date" +
                ") daily_dau_values";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, BOT_SEND);
            ps.setString(2, BOT_SEND);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            log.error("公开历史平均 DAU 查询失败: {}", e.getMessage(), e);
        }
        return 0;
    }

    public static UserMessageStats queryUserMessageStats(String userOpenId) {
        if (isBlank(userOpenId)) {
            return new UserMessageStats(0, 0, 0, null, null, null);
        }

        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM `" + C2C_TABLE + "` WHERE union_openId = ? AND source <> '" + BOT_SEND + "') AS c2c_received, " +
                "(SELECT COUNT(*) FROM `" + C2C_TABLE + "` WHERE union_openId = ? AND source = '" + BOT_SEND + "') AS c2c_sent, " +
                "(SELECT COUNT(*) FROM `" + GROUP_TABLE + "` WHERE union_openId = ? AND event_type <> '" + BOT_SEND + "') AS group_received, " +
                "(SELECT MIN(first_seen) FROM (" +
                "  SELECT MIN(created_at) AS first_seen FROM `" + C2C_TABLE + "` WHERE union_openId = ? " +
                "  UNION ALL SELECT MIN(created_at) FROM `" + GROUP_TABLE + "` WHERE union_openId = ?" +
                ") t) AS first_seen_at, " +
                "(SELECT MAX(last_seen) FROM (" +
                "  SELECT MAX(created_at) AS last_seen FROM `" + C2C_TABLE + "` WHERE union_openId = ? " +
                "  UNION ALL SELECT MAX(created_at) FROM `" + GROUP_TABLE + "` WHERE union_openId = ?" +
                ") t) AS last_seen_at, " +
                "(SELECT username FROM (" +
                "  SELECT username, created_at FROM `" + C2C_TABLE + "` WHERE union_openId = ? AND source <> '" + BOT_SEND + "' AND username IS NOT NULL " +
                "  UNION ALL SELECT username, created_at FROM `" + GROUP_TABLE + "` WHERE union_openId = ? AND event_type <> '" + BOT_SEND + "' AND username IS NOT NULL" +
                ") u ORDER BY created_at DESC LIMIT 1) AS last_username";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 9; i++) {
                ps.setString(i, userOpenId);
            }
            var rs = ps.executeQuery();
            if (rs.next()) {
                return new UserMessageStats(
                        rs.getLong("c2c_received"),
                        rs.getLong("c2c_sent"),
                        rs.getLong("group_received"),
                        toString(rs.getTimestamp("first_seen_at")),
                        toString(rs.getTimestamp("last_seen_at")),
                        rs.getString("last_username")
                );
            }
        } catch (SQLException e) {
            log.error("公开用户信息统计查询失败, userOpenId={}: {}", userOpenId, e.getMessage(), e);
        }
        return new UserMessageStats(0, 0, 0, null, null, null);
    }

    public static GroupMessageStats queryGroupMessageStats(String groupOpenId) {
        if (isBlank(groupOpenId)) {
            return new GroupMessageStats(0, 0, 0, null, null);
        }

        String sql = "SELECT " +
                "COUNT(CASE WHEN event_type <> '" + BOT_SEND + "' THEN 1 END) AS received, " +
                "COUNT(CASE WHEN event_type = '" + BOT_SEND + "' THEN 1 END) AS sent, " +
                "COUNT(DISTINCT CASE WHEN event_type <> '" + BOT_SEND + "' THEN union_openId END) AS active_users, " +
                "MIN(created_at) AS first_seen_at, " +
                "MAX(created_at) AS last_seen_at " +
                "FROM `" + GROUP_TABLE + "` WHERE group_openId = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return new GroupMessageStats(
                        rs.getLong("received"),
                        rs.getLong("sent"),
                        rs.getLong("active_users"),
                        toString(rs.getTimestamp("first_seen_at")),
                        toString(rs.getTimestamp("last_seen_at"))
                );
            }
        } catch (SQLException e) {
            log.error("公开群聊信息统计查询失败, groupOpenId={}: {}", groupOpenId, e.getMessage(), e);
        }
        return new GroupMessageStats(0, 0, 0, null, null);
    }

    private static long countDistinctGroupUsers(boolean botSent, LocalDateTime start, LocalDateTime end, String groupOpenId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT union_openId) FROM `")
                .append(GROUP_TABLE)
                .append("` WHERE event_type ");
        appendBotSendPredicate(sql, botSent);
        sql.append(" AND union_openId IS NOT NULL");
        boolean hasGroup = !isBlank(groupOpenId);
        appendTimeRange(sql, start, end);
        if (hasGroup) {
            sql.append(" AND group_openId = ?");
        }

        return queryLong(sql.toString(), ps -> {
            int idx = 1;
            ps.setString(idx++, BOT_SEND);
            idx = bindTimeRange(ps, idx, start, end);
            if (hasGroup) {
                ps.setString(idx, groupOpenId);
            }
        });
    }

    private static long countDistinctGroupTargets(boolean botSent, LocalDateTime start, LocalDateTime end, String groupOpenId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT group_openId) FROM `")
                .append(GROUP_TABLE)
                .append("` WHERE event_type ");
        appendBotSendPredicate(sql, botSent);
        sql.append(" AND group_openId IS NOT NULL");
        boolean hasGroup = !isBlank(groupOpenId);
        appendTimeRange(sql, start, end);
        if (hasGroup) {
            sql.append(" AND group_openId = ?");
        }

        return queryLong(sql.toString(), ps -> {
            int idx = 1;
            ps.setString(idx++, BOT_SEND);
            idx = bindTimeRange(ps, idx, start, end);
            if (hasGroup) {
                ps.setString(idx, groupOpenId);
            }
        });
    }

    private static long countDistinctC2CUsers(boolean botSent, LocalDateTime start, LocalDateTime end, String userOpenId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT union_openId) FROM `")
                .append(C2C_TABLE)
                .append("` WHERE source ");
        appendBotSendPredicate(sql, botSent);
        sql.append(" AND union_openId IS NOT NULL");
        boolean hasUser = !isBlank(userOpenId);
        appendTimeRange(sql, start, end);
        if (hasUser) {
            sql.append(" AND union_openId = ?");
        }

        return queryLong(sql.toString(), ps -> {
            int idx = 1;
            ps.setString(idx++, BOT_SEND);
            idx = bindTimeRange(ps, idx, start, end);
            if (hasUser) {
                ps.setString(idx, userOpenId);
            }
        });
    }

    private static long countDistinctReceiveUsers(LocalDateTime start, LocalDateTime end, String groupOpenId, String userOpenId) {
        boolean hasGroup = !isBlank(groupOpenId);
        boolean hasUser = !isBlank(userOpenId);
        StringBuilder groupSql = new StringBuilder("SELECT union_openId AS user_id FROM `")
                .append(GROUP_TABLE)
                .append("` WHERE event_type <> ? AND union_openId IS NOT NULL");
        appendTimeRange(groupSql, start, end);
        if (hasGroup) {
            groupSql.append(" AND group_openId = ?");
        }

        StringBuilder c2cSql = new StringBuilder("SELECT union_openId AS user_id FROM `")
                .append(C2C_TABLE)
                .append("` WHERE source <> ? AND union_openId IS NOT NULL");
        appendTimeRange(c2cSql, start, end);
        if (hasUser) {
            c2cSql.append(" AND union_openId = ?");
        }

        String sql = "SELECT COUNT(*) FROM (" + groupSql + " UNION " + c2cSql + ") active_users";
        return queryLong(sql, ps -> {
            int idx = 1;
            ps.setString(idx++, BOT_SEND);
            idx = bindTimeRange(ps, idx, start, end);
            if (hasGroup) {
                ps.setString(idx++, groupOpenId);
            }
            ps.setString(idx++, BOT_SEND);
            idx = bindTimeRange(ps, idx, start, end);
            if (hasUser) {
                ps.setString(idx, userOpenId);
            }
        });
    }

    /**
     * 按天聚合消息量与 DAU，供统计页画折线图
     *
     * @param start 起始时间，闭区间
     * @param end   结束时间，开区间
     * @return 按日期升序排列的数据点，仅包含有记录的日期
     */
    public static List<DailyPoint> queryDailySeries(LocalDateTime start, LocalDateTime end) {
        Map<String, long[]> buckets = new TreeMap<>();

        // 四个消息量指标：用 UNION ALL 把两张表拼起来后按日期聚合，避免四次往返
        String messageSql = "SELECT d, SUM(g_recv) AS g_recv, SUM(g_sent) AS g_sent, " +
                "SUM(c_recv) AS c_recv, SUM(c_sent) AS c_sent FROM (" +
                "  SELECT DATE(created_at) AS d," +
                "    CASE WHEN event_type <> ? THEN 1 ELSE 0 END AS g_recv," +
                "    CASE WHEN event_type = ? THEN 1 ELSE 0 END AS g_sent," +
                "    0 AS c_recv, 0 AS c_sent" +
                "  FROM `" + GROUP_TABLE + "` WHERE created_at >= ? AND created_at < ?" +
                "  UNION ALL" +
                "  SELECT DATE(created_at) AS d, 0, 0," +
                "    CASE WHEN source <> ? THEN 1 ELSE 0 END," +
                "    CASE WHEN source = ? THEN 1 ELSE 0 END" +
                "  FROM `" + C2C_TABLE + "` WHERE created_at >= ? AND created_at < ?" +
                ") t GROUP BY d ORDER BY d";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(messageSql)) {
            ps.setString(1, BOT_SEND);
            ps.setString(2, BOT_SEND);
            ps.setTimestamp(3, Timestamp.valueOf(start));
            ps.setTimestamp(4, Timestamp.valueOf(end));
            ps.setString(5, BOT_SEND);
            ps.setString(6, BOT_SEND);
            ps.setTimestamp(7, Timestamp.valueOf(start));
            ps.setTimestamp(8, Timestamp.valueOf(end));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    buckets.computeIfAbsent(rs.getString("d"), k -> new long[5])[0] = rs.getLong("g_recv");
                    long[] row = buckets.get(rs.getString("d"));
                    row[1] = rs.getLong("g_sent");
                    row[2] = rs.getLong("c_recv");
                    row[3] = rs.getLong("c_sent");
                }
            }
        } catch (SQLException e) {
            log.error("按日聚合消息量失败: {}", e.getMessage(), e);
        }

        // DAU 需要跨两张表去重，不能和上面的求和混在一次聚合里
        String dauSql = "SELECT active_date, COUNT(DISTINCT user_id) AS dau FROM (" +
                "  SELECT DATE(created_at) AS active_date, union_openId AS user_id" +
                "  FROM `" + GROUP_TABLE + "` WHERE event_type <> ? AND created_at >= ? AND created_at < ?" +
                "  UNION ALL" +
                "  SELECT DATE(created_at) AS active_date, union_openId AS user_id" +
                "  FROM `" + C2C_TABLE + "` WHERE source <> ? AND created_at >= ? AND created_at < ?" +
                ") t GROUP BY active_date ORDER BY active_date";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(dauSql)) {
            ps.setString(1, BOT_SEND);
            ps.setTimestamp(2, Timestamp.valueOf(start));
            ps.setTimestamp(3, Timestamp.valueOf(end));
            ps.setString(4, BOT_SEND);
            ps.setTimestamp(5, Timestamp.valueOf(start));
            ps.setTimestamp(6, Timestamp.valueOf(end));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    buckets.computeIfAbsent(rs.getString("active_date"), k -> new long[5])[4] = rs.getLong("dau");
                }
            }
        } catch (SQLException e) {
            log.error("按日聚合 DAU 失败: {}", e.getMessage(), e);
        }

        List<DailyPoint> points = new ArrayList<>(buckets.size());
        buckets.forEach((date, v) -> points.add(new DailyPoint(date, v[0], v[1], v[2], v[3], v[4])));
        return points;
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

    private static void appendTimeRange(StringBuilder sql, LocalDateTime start, LocalDateTime end) {
        if (start != null) {
            sql.append(" AND created_at >= ?");
        }
        if (end != null) {
            sql.append(" AND created_at < ?");
        }
    }

    private static int bindTimeRange(PreparedStatement ps, int idx, LocalDateTime start, LocalDateTime end) throws SQLException {
        if (start != null) {
            ps.setTimestamp(idx++, Timestamp.valueOf(start));
        }
        if (end != null) {
            ps.setTimestamp(idx++, Timestamp.valueOf(end));
        }
        return idx;
    }

    private static String toString(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
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
