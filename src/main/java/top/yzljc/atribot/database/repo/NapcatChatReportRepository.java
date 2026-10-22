package top.yzljc.atribot.database.repo;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Napcat 日报按北京时间读取消息，结束时间使用不包含端点，避免遗漏当天最后一秒。
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatChatReportRepository
 * @Created_at 2026/10/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public final class NapcatChatReportRepository {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private NapcatChatReportRepository() {}

    public record Window(LocalDate date, Instant start, Instant end) {
        public static Window yesterday(Clock clock) {
            return day(LocalDate.now(clock.withZone(ZONE)).minusDays(1));
        }

        public static Window day(LocalDate date) {
            return new Window(date, date.atStartOfDay(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant());
        }

        /** 自动日报统计当天零点至 23 点；任务延迟执行也不将之后的消息混入。 */
        public static Window automatic(Clock clock) {
            LocalDate date = LocalDate.now(clock.withZone(ZONE));
            Instant cutoff = date.atTime(23, 0).atZone(ZONE).toInstant();
            return new Window(date, date.atStartOfDay(ZONE).toInstant(),
                    clock.instant().isBefore(cutoff) ? clock.instant() : cutoff);
        }
    }

    public record Message(long messageId, long userId, long timestamp, String content) {}

    public static List<Message> query(Connection connection, String groupId, Window window) throws SQLException {
        if (groupId == null || !groupId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("群号必须是正整数");
        String table = "qq_group_message_record_" + Long.parseLong(groupId);
        String sql = "SELECT message_id, user_id, msg_time, raw_message FROM `" + table
                + "` WHERE group_id = ? AND msg_time >= ? AND msg_time < ? ORDER BY msg_time, id";
        List<Message> result = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Long.parseLong(groupId));
            statement.setLong(2, window.start().getEpochSecond());
            statement.setLong(3, window.end().getEpochSecond());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.add(new Message(rows.getLong("message_id"), rows.getLong("user_id"),
                        rows.getLong("msg_time"), rows.getString("raw_message")));
            }
        } catch (SQLException e) {
            // 群首次开启或尚无消息时，分表可能尚未创建。其余数据库故障必须向上报告。
            if (!"42S02".equals(e.getSQLState()) && !"42S04".equals(e.getSQLState()) && e.getErrorCode() != 1146) throw e;
        }
        return result;
    }
}
