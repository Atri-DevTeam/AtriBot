package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
* @Author AndyOctopus
* @ClassName ModerationLogRepository
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.database.repo
*/
@Slf4j
public final class ModerationLogRepository {

    private static final String TABLE = "group_moderation_log";

    public static final String CATEGORY_KEYWORD_RECALL = "KEYWORD_RECALL";
    public static final String CATEGORY_AI_RECALL = "AI_RECALL";
    public static final String CATEGORY_JOIN_REVIEW = "JOIN_REVIEW";

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `" + TABLE + "` (" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT," +
                "  `group_open_id` VARCHAR(256) NOT NULL," +
                "  `category` VARCHAR(32) NOT NULL," +
                "  `action` VARCHAR(64) NOT NULL," +
                "  `target_member_open_id` VARCHAR(256) NULL," +
                "  `detail` TEXT NULL," +
                "  `created_at` DATETIME NOT NULL," +
                "  PRIMARY KEY (`id`)," +
                "  KEY `idx_group` (`group_open_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
        } catch (Exception e) {
            log.error("初始化群管系统日志表失败", e);
        }
    }

    public static void log(String groupOpenId, String category, String action, String targetMemberOpenId, String detail) {
        String sql = "INSERT INTO `" + TABLE + "` (group_open_id, category, action, target_member_open_id, detail, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, category);
            ps.setString(3, action);
            ps.setString(4, targetMemberOpenId);
            ps.setString(5, detail);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            ps.execute();
        } catch (Exception e) {
            log.error("写入群管系统日志失败", e);
        }
    }

    public static List<LogRow> findPaginated(String groupOpenId, int page, int pageSize, String category, String keyword) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT id, group_open_id, category, action, target_member_open_id, detail, created_at FROM `"
                + TABLE + "`" + buildWhere(groupOpenId, category, keyword, params)
                + " ORDER BY id DESC LIMIT ? OFFSET ?";
        int size = Math.max(pageSize, 1);
        int offset = Math.max(page - 1, 0) * size;
        List<LogRow> rows = new ArrayList<>();
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            bind(ps, params);
            ps.setInt(params.size() + 1, size);
            ps.setInt(params.size() + 2, offset);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LogRow(
                            rs.getLong("id"),
                            rs.getString("group_open_id"),
                            rs.getString("category"),
                            rs.getString("action"),
                            rs.getString("target_member_open_id"),
                            rs.getString("detail"),
                            rs.getTimestamp("created_at")));
                }
            }
        } catch (Exception e) {
            log.error("查询群管系统日志失败", e);
        }
        return rows;
    }

    public static int count(String groupOpenId, String category, String keyword) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM `" + TABLE + "`" + buildWhere(groupOpenId, category, keyword, params);
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            log.error("统计群管系统日志失败", e);
            return 0;
        }
    }

    public static Stats stats(String groupOpenId) {
        return new Stats(
                count(groupOpenId, null, null),
                countSince(groupOpenId, todayStartMillis()),
                countSince(groupOpenId, System.currentTimeMillis() - 24L * 3600_000L),
                count(groupOpenId, CATEGORY_KEYWORD_RECALL, null),
                count(groupOpenId, CATEGORY_AI_RECALL, null),
                count(groupOpenId, CATEGORY_JOIN_REVIEW, null)
        );
    }

    private static int countSince(String groupOpenId, long fromMillis) {
        String sql = "SELECT COUNT(*) FROM `" + TABLE + "` WHERE group_open_id = ? AND created_at >= ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setTimestamp(2, new Timestamp(fromMillis));
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            log.error("统计群管系统日志失败", e);
            return 0;
        }
    }

    private static long todayStartMillis() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return LocalDateTime.of(today, java.time.LocalTime.MIDNIGHT)
                .atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
    }

    private static String buildWhere(String groupOpenId, String category, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE group_open_id = ?");
        params.add(groupOpenId);
        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            where.append(" AND `category` = ?");
            params.add(category.toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (`action` LIKE ? OR `target_member_open_id` LIKE ? OR `detail` LIKE ?)");
            String like = "%" + keyword + "%";
            for (int i = 0; i < 3; i++) {
                params.add(like);
            }
        }
        return where.toString();
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws java.sql.SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    public record LogRow(long id, String groupOpenId, String category, String action,
                         String targetMemberOpenId, String detail, Timestamp createdAt) {
    }

    public record Stats(int all, int today, int last24h, int keywordRecall, int aiRecall, int joinReview) {
    }
}
