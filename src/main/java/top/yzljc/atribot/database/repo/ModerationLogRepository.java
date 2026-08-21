package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Timestamp;
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

    public static List<LogRow> list(String groupOpenId, int page, int pageSize) {
        List<LogRow> rows = new ArrayList<>();
        int size = Math.max(pageSize, 1);
        int offset = Math.max(page - 1, 0) * size;
        String sql = "SELECT id, group_open_id, category, action, target_member_open_id, detail, created_at FROM `"
                + TABLE + "` WHERE group_open_id = ? ORDER BY id DESC LIMIT ? OFFSET ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setInt(2, size);
            ps.setInt(3, offset);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LogRow(
                            rs.getLong("id"),
                            rs.getString("group_open_id"),
                            rs.getString("category"),
                            rs.getString("action"),
                            rs.getString("target_member_open_id"),
                            rs.getString("detail"),
                            rs.getTimestamp("created_at").toString()));
                }
            }
        } catch (Exception e) {
            log.error("查询群管系统日志失败", e);
        }
        return rows;
    }

    public record LogRow(long id, String groupOpenId, String category, String action,
                         String targetMemberOpenId, String detail, String createdAt) {
    }
}
