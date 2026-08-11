package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.EventLogDTO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class EventLogRepository {

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `raw_event_log` (" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT," +
                "  `event_type` VARCHAR(64) NOT NULL," +
                "  `event_id` VARCHAR(255) NULL," +
                "  `seq` INT NULL," +
                "  `raw_data` LONGTEXT NOT NULL," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  KEY `idx_event_type_create_time` (`event_type`, `create_time`)," +
                "  KEY `idx_create_time` (`create_time`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection()) {
            try (var ps = con.prepareStatement(sql)) {
                ps.execute();
            }
            log.info("原始事件日志表初始化完成");
        } catch (Exception e) {
            log.error("初始化原始事件日志表失败", e);
        }
    }

    public static void record(String eventType, String eventId, Integer seq, String rawData) {
        String sql = "INSERT INTO `raw_event_log` (`event_type`, `event_id`, `seq`, `raw_data`, `create_time`) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, eventId);
            if (seq == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, seq);
            ps.setString(4, rawData);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("写入原始事件日志失败: eventType={}", eventType, e);
        }
    }

    public static List<EventLogDTO> findPaginated(int page, int pageSize, String eventType, String keyword) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT `id`, `event_type`, `event_id`, `seq`, LEFT(`raw_data`, 400) AS `raw_data`, `create_time` " +
                "FROM `raw_event_log`" + buildWhere(eventType, keyword, params) +
                " ORDER BY `create_time` DESC, `id` DESC LIMIT ? OFFSET ?";
        params.add(pageSize);
        params.add((page - 1) * pageSize);

        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                List<EventLogDTO> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(rowToDTO(rs));
                }
                return result;
            }
        } catch (Exception e) {
            log.error("分页查询原始事件日志失败", e);
            return List.of();
        }
    }

    public static EventLogDTO findById(long id) {
        String sql = "SELECT * FROM `raw_event_log` WHERE `id` = ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToDTO(rs);
                }
            }
        } catch (Exception e) {
            log.error("按 id 查询原始事件日志失败: id={}", id, e);
        }
        return null;
    }

    public static int count(String eventType, String keyword) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM `raw_event_log`" + buildWhere(eventType, keyword, params);
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("统计原始事件日志失败", e);
        }
        return 0;
    }

    /**
     * 删除指定时间范围内的事件（start/end 为 epoch 毫秒，可为空）
     * start、end、eventType 全部为空时表示清空全部记录
     *
     * @return 删除的条数
     */
    public static int deleteByRange(Long start, Long end, String eventType) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("DELETE FROM `raw_event_log` WHERE 1=1");
        if (start != null) {
            sql.append(" AND `create_time` >= ?");
            params.add(new Timestamp(start));
        }
        if (end != null) {
            sql.append(" AND `create_time` <= ?");
            params.add(new Timestamp(end));
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND `event_type` = ?");
            params.add(eventType);
        }

        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql.toString())) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (Exception e) {
            log.error("按时间范围删除原始事件日志失败", e);
            return 0;
        }
    }

    public static EventLogStats stats() {
        return new EventLogStats(
                count(null, null),
                countSince(todayStartMillis()),
                countSince(System.currentTimeMillis() - 24L * 3600_000L),
                topTypes()
        );
    }

    private static int countSince(long fromMillis) {
        String sql = "SELECT COUNT(*) FROM `raw_event_log` WHERE `create_time` >= ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(fromMillis));
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("统计原始事件日志失败", e);
        }
        return 0;
    }

    /** 统计全部事件类型的数量分布，按数量降序 */
    private static List<EventTypeCount> topTypes() {
        String sql = "SELECT `event_type`, COUNT(*) AS c FROM `raw_event_log` GROUP BY `event_type` ORDER BY c DESC";
        List<EventTypeCount> result = new ArrayList<>();
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new EventTypeCount(rs.getString("event_type"), rs.getInt("c")));
                }
            }
        } catch (Exception e) {
            log.error("统计原始事件日志类型分布失败", e);
        }
        return result;
    }

    private static long todayStartMillis() {
        return java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String buildWhere(String eventType, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder();
        if (eventType != null && !eventType.isBlank() && !"ALL".equalsIgnoreCase(eventType)) {
            where.append(" WHERE `event_type` = ?");
            params.add(eventType);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(where.isEmpty() ? " WHERE " : " AND ")
                    .append("(`event_type` LIKE ? OR `event_id` LIKE ? OR `raw_data` LIKE ?)");
            String like = "%" + keyword + "%";
            for (int i = 0; i < 3; i++) {
                params.add(like);
            }
        }
        return where.toString();
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static EventLogDTO rowToDTO(ResultSet rs) throws SQLException {
        EventLogDTO dto = new EventLogDTO();
        dto.setId(rs.getLong("id"));
        dto.setEventType(rs.getString("event_type"));
        dto.setEventId(rs.getString("event_id"));
        int seq = rs.getInt("seq");
        dto.setSeq(rs.wasNull() ? null : seq);
        dto.setRawData(rs.getString("raw_data"));
        dto.setCreateTime(rs.getTimestamp("create_time"));
        return dto;
    }

    public record EventLogStats(int all, int today, int last24h, List<EventTypeCount> types) {
    }

    public record EventTypeCount(String eventType, int count) {
    }
}
