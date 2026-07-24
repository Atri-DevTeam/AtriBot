package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.PendingNoticeDTO;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 待送达通知数据库访问层
 *
 * @Author YZ_Ljc_
 * @ClassName PendingNoticeRepository
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class PendingNoticeRepository {

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `pending_notice` (" +
                "  `id` VARCHAR(36) NOT NULL," +
                "  `target_type` VARCHAR(24) NOT NULL," +
                "  `target_id` VARCHAR(255) NOT NULL," +
                "  `mention_user_id` VARCHAR(255) NULL," +
                "  `content` MEDIUMTEXT NOT NULL," +
                "  `source` VARCHAR(32) NULL," +
                "  `source_id` VARCHAR(64) NULL," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  `is_delivered` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `deliver_time` DATETIME NULL," +
                "  `attempts` INT NOT NULL DEFAULT 0," +
                "  `last_error` TEXT NULL," +
                "  PRIMARY KEY (`id`)," +
                "  INDEX `idx_pending` (`target_id`, `is_delivered`)," +
                "  INDEX `idx_source` (`source`, `source_id`)," +
                "  INDEX `idx_create_time` (`create_time`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
            log.info("待送达通知数据库表初始化完成");
        } catch (Exception e) {
            log.error("初始化待送达通知数据库表失败", e);
        }
    }

    /**
     * 入队一条待送达通知，返回生成的主键；失败返回 null。
     */
    public static String enqueue(PendingNoticeDTO notice) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO `pending_notice` (`id`, `target_type`, `target_id`, `mention_user_id`, `content`, " +
                "`source`, `source_id`, `create_time`, `is_delivered`, `attempts`, `last_error`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, notice.getTargetType());
            ps.setString(3, notice.getTargetId());
            ps.setString(4, notice.getMentionUserId());
            ps.setString(5, notice.getContent());
            ps.setString(6, notice.getSource());
            ps.setString(7, notice.getSourceId());
            ps.setTimestamp(8, notice.getCreateTime() != null ? notice.getCreateTime() : new Timestamp(System.currentTimeMillis()));
            ps.setInt(9, notice.getAttempts());
            ps.setString(10, notice.getLastError());
            ps.executeUpdate();
            log.info("通知已加入被动队列: id={}, target={}({}), source={}",
                    id, notice.getTargetId(), notice.getTargetType(), notice.getSource());
            return id;
        } catch (Exception e) {
            log.error("通知入队失败: target={}", notice.getTargetId(), e);
            return null;
        }
    }

    /**
     * 取出该私聊用户最早一条待送达通知。
     */
    public static PendingNoticeDTO pollForC2C(String userOpenId) {
        if (userOpenId == null || userOpenId.isBlank()) return null;
        String sql = "SELECT * FROM `pending_notice` WHERE `is_delivered` = FALSE AND `target_type` = 'OFFICIAL_C2C' " +
                "AND `target_id` = ? ORDER BY `create_time` ASC LIMIT 1";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rowToDTO(rs);
            }
        } catch (Exception e) {
            log.error("查询私聊待送达通知失败: user={}", userOpenId, e);
        }
        return null;
    }

    /**
     * 取出该群最早一条待送达通知。
     *
     * <p>投稿类通知会绑定 mention_user_id，只有本人在群里发言时才补发，避免打扰其他群友；
     * mention_user_id 为空的通知则任意群成员发言都可触发。
     */
    public static PendingNoticeDTO pollForGroup(String groupOpenId, String speakerUserId) {
        if (groupOpenId == null || groupOpenId.isBlank()) return null;
        String sql = "SELECT * FROM `pending_notice` WHERE `is_delivered` = FALSE AND `target_type` = 'OFFICIAL_GROUP' " +
                "AND `target_id` = ? AND (`mention_user_id` IS NULL OR `mention_user_id` = ?) " +
                "ORDER BY `create_time` ASC LIMIT 1";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, speakerUserId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rowToDTO(rs);
            }
        } catch (Exception e) {
            log.error("查询群聊待送达通知失败: group={}", groupOpenId, e);
        }
        return null;
    }

    public static boolean markDelivered(String id) {
        String sql = "UPDATE `pending_notice` SET `is_delivered` = TRUE, `deliver_time` = ? WHERE `id` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setString(2, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("标记通知已送达失败: id={}", id, e);
            return false;
        }
    }

    /**
     * 记录一次投递失败，用于排查长期卡住的通知。
     */
    public static void markAttemptFailed(String id, String error) {
        String sql = "UPDATE `pending_notice` SET `attempts` = `attempts` + 1, `last_error` = ? WHERE `id` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, error != null && error.length() > 500 ? error.substring(0, 500) : error);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("记录通知投递失败信息出错: id={}", id, e);
        }
    }

    /**
     * 某业务对象是否已有未送达的通知，避免重复入队。
     */
    public static boolean hasPending(String source, String sourceId) {
        String sql = "SELECT 1 FROM `pending_notice` WHERE `source` = ? AND `source_id` = ? AND `is_delivered` = FALSE LIMIT 1";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, source);
            ps.setString(2, sourceId);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("查询通知是否已入队失败: source={}, sourceId={}", source, sourceId, e);
            return false;
        }
    }

    public static int countPending() {
        String sql = "SELECT COUNT(*) FROM `pending_notice` WHERE `is_delivered` = FALSE";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            log.error("统计待送达通知失败", e);
        }
        return 0;
    }

    public static List<PendingNoticeDTO> findPending(int limit) {
        String sql = "SELECT * FROM `pending_notice` WHERE `is_delivered` = FALSE ORDER BY `create_time` ASC LIMIT ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                List<PendingNoticeDTO> list = new ArrayList<>();
                while (rs.next()) list.add(rowToDTO(rs));
                return list;
            }
        } catch (Exception e) {
            log.error("查询待送达通知列表失败", e);
            return List.of();
        }
    }

    private static PendingNoticeDTO rowToDTO(java.sql.ResultSet rs) throws java.sql.SQLException {
        PendingNoticeDTO dto = new PendingNoticeDTO();
        dto.setId(rs.getString("id"));
        dto.setTargetType(rs.getString("target_type"));
        dto.setTargetId(rs.getString("target_id"));
        dto.setMentionUserId(rs.getString("mention_user_id"));
        dto.setContent(rs.getString("content"));
        dto.setSource(rs.getString("source"));
        dto.setSourceId(rs.getString("source_id"));
        dto.setCreateTime(rs.getTimestamp("create_time"));
        dto.setDelivered(rs.getBoolean("is_delivered"));
        dto.setDeliverTime(rs.getTimestamp("deliver_time"));
        dto.setAttempts(rs.getInt("attempts"));
        dto.setLastError(rs.getString("last_error"));
        return dto;
    }
}