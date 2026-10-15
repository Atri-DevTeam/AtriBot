package top.yzljc.atribot.database.repo;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupDailyReportRepository
 * @Created_at 2026/10/14
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public final class GroupDailyReportRepository {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SQL = """
            SELECT union_openId AS user_open_id, COUNT(*) AS message_count,
                   RANK() OVER (ORDER BY COUNT(*) DESC) AS position,
                   SUM(COUNT(*)) OVER () AS total_messages,
                   COUNT(*) OVER () AS total_members
            FROM official_group_record
            WHERE group_openId = ?
              AND created_at >= FROM_UNIXTIME(?) AND created_at < FROM_UNIXTIME(?)
              AND event_type IN ('GROUP_MESSAGE_CREATE', 'GROUP_AT_MESSAGE_CREATE', 'BOT_SEND')
              AND union_openId IS NOT NULL AND union_openId <> ''
            GROUP BY union_openId
            ORDER BY message_count DESC, union_openId ASC
            LIMIT 10
            """;

    private GroupDailyReportRepository() {
    }

    /**
     * 按北京时间的入库日期统计；范围条件直接使用 created_at，保留组合索引的可用性。
     * 同分并列，最多返回十名成员；总数包含未上榜成员。查询不读取消息正文、昵称或头像。
     *
     * @param connection  调用方持有的连接，本方法不会关闭连接
     * @param groupOpenId 官机群 OpenID
     * @param date        统计日期
     * @return 当日群消息汇总，包含机器人消息；空记录返回零计数和空列表
     * @throws SQLException 查询失败或超时时抛出
     */
    public static Report query(Connection connection, String groupOpenId, LocalDate date) throws SQLException {
        List<Entry> entries = new ArrayList<>();
        long messages = 0;
        long members = 0;
        try (var statement = connection.prepareStatement(SQL)) {
            statement.setQueryTimeout(10);
            statement.setString(1, groupOpenId);
            statement.setLong(2, date.atStartOfDay(ZONE).toEpochSecond());
            statement.setLong(3, date.plusDays(1).atStartOfDay(ZONE).toEpochSecond());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    messages = rows.getLong("total_messages");
                    members = rows.getLong("total_members");
                    entries.add(new Entry(rows.getString("user_open_id"), rows.getLong("message_count"),
                            rows.getInt("position")));
                }
            }
        }
        return new Report(date, messages, members, List.copyOf(entries));
    }

    public record Entry(String userOpenId, long messages, int position) {
    }

    public record Report(LocalDate date, long messages, long members, List<Entry> entries) {
    }
}
