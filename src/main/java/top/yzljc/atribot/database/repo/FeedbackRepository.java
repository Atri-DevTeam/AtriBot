package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.FeedbackDTO;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 反馈数据库访问层
 *
 * @Author YZ_Ljc_
 * @ClassName FeedbackRepository
 * @Created_at 2026/06/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class FeedbackRepository {

    // ==================== Table init ====================

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `feedback` (" +
                "  `id` VARCHAR(36) NOT NULL," +
                "  `platform` VARCHAR(32) NOT NULL," +
                "  `user_id` VARCHAR(255) NOT NULL," +
                "  `username` VARCHAR(255) NOT NULL," +
                "  `group_id` VARCHAR(255) NULL," +
                "  `submit_content` TEXT NOT NULL," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  `is_read` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `reply_content` TEXT NULL," +
                "  `reply_time` DATETIME NULL," +
                "  `is_hidden` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  PRIMARY KEY (`id`)," +
                "  INDEX `idx_user_id` (`user_id`)," +
                "  INDEX `idx_is_read` (`is_read`)," +
                "  INDEX `idx_create_time` (`create_time`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
            log.info("反馈数据库表初始化完成");
        } catch (Exception e) {
            log.error("初始化反馈数据库表失败", e);
        }
    }

    // ==================== 提交反馈 ====================

    /**
     * 提交新反馈，返回生成的 UUID
     */
    public static String insert(FeedbackDTO feedback) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO `feedback` (`id`, `platform`, `user_id`, `username`, `group_id`, `submit_content`, `create_time`, `is_read`, `is_hidden`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, FALSE)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, feedback.getPlatform());
            ps.setString(3, feedback.getUserId());
            ps.setString(4, feedback.getUsername());
            ps.setString(5, feedback.getGroupId());
            ps.setString(6, feedback.getSubmitContent());
            ps.setTimestamp(7, feedback.getCreateTime() != null ? feedback.getCreateTime() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            log.info("反馈提交成功: id={}, userId={}", id, feedback.getUserId());
            return id;
        } catch (Exception e) {
            log.error("提交反馈失败: userId={}", feedback.getUserId(), e);
            return null;
        }
    }

    // ==================== 查询反馈 ====================

    /**
     * 查询用户是否有未回复的反馈
     */
    public static boolean hasPendingFeedback(String userId) {
        String sql = "SELECT 1 FROM `feedback` WHERE `user_id` = ? AND `reply_content` IS NULL LIMIT 1";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("查询用户待处理反馈失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 查询用户是否有待送达的回复
     */
    public static FeedbackDTO findPendingReplyByUserId(String userId) {
        String sql = "SELECT * FROM `feedback` WHERE `user_id` = ? AND `reply_content` IS NOT NULL AND `is_read` = FALSE LIMIT 1";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToDTO(rs);
                }
            }
        } catch (Exception e) {
            log.error("查询用户待送达回复失败: userId={}", userId, e);
        }
        return null;
    }

    /**
     * 标记回复为已读（已送达）
     */
    public static boolean markRead(String id) {
        String sql = "UPDATE `feedback` SET `is_read` = TRUE WHERE `id` = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("标记反馈已读失败: id={}", id, e);
            return false;
        }
    }

    // ==================== 管理员操作 ====================

    /**
     * 获取所有未读反馈列表（按时间倒序）
     */
    public static List<FeedbackDTO> findAllUnread() {
        String sql = "SELECT * FROM `feedback` WHERE `is_read` = FALSE ORDER BY `create_time` DESC";
        return queryList(sql);
    }

    /**
     * 获取所有未回复的反馈列表（按时间倒序）
     */
    public static List<FeedbackDTO> findAllUnreplied() {
        String sql = "SELECT * FROM `feedback` WHERE `reply_content` IS NULL ORDER BY `create_time` DESC";
        return queryList(sql);
    }

    /**
     * 分页获取未回复反馈
     */
    public static List<FeedbackDTO> findUnrepliedPaginated(int page, int pageSize) {
        String sql = "SELECT * FROM `feedback` WHERE `reply_content` IS NULL ORDER BY `create_time` DESC LIMIT ? OFFSET ?";
        return paginatedQuery(sql, page, pageSize);
    }

    /**
     * 获取未回复反馈总数
     */
    public static int countUnreplied() {
        String sql = "SELECT COUNT(*) FROM `feedback` WHERE `reply_content` IS NULL";
        return count(sql);
    }

    /**
     * 获取已回复反馈总数
     */
    public static int countReplied() {
        String sql = "SELECT COUNT(*) FROM `feedback` WHERE `reply_content` IS NOT NULL";
        return count(sql);
    }

    /**
     * 获取所有反馈总数
     */
    public static int countAll() {
        String sql = "SELECT COUNT(*) FROM `feedback`";
        return count(sql);
    }

    /**
     * 分页获取已回复反馈
     */
    public static List<FeedbackDTO> findRepliedPaginated(int page, int pageSize) {
        String sql = "SELECT * FROM `feedback` WHERE `reply_content` IS NOT NULL ORDER BY `create_time` DESC LIMIT ? OFFSET ?";
        return paginatedQuery(sql, page, pageSize);
    }

    /**
     * 分页获取所有反馈
     */
    public static List<FeedbackDTO> findAllPaginated(int page, int pageSize) {
        String sql = "SELECT * FROM `feedback` ORDER BY `create_time` DESC LIMIT ? OFFSET ?";
        return paginatedQuery(sql, page, pageSize);
    }

    /**
     * 根据 ID 查找反馈
     */
    public static FeedbackDTO findById(String id) {
        String sql = "SELECT * FROM `feedback` WHERE `id` = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToDTO(rs);
                }
            }
        } catch (Exception e) {
            log.error("根据ID查询反馈失败: id={}", id, e);
        }
        return null;
    }

    /**
     * 根据 ID 前缀查找反馈（用于命令行的简写匹配）
     */
    public static FeedbackDTO findByIdPrefix(String idPrefix) {
        if (idPrefix == null || idPrefix.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM `feedback` WHERE `id` LIKE ? LIMIT 1";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, idPrefix + "%");
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToDTO(rs);
                }
            }
        } catch (Exception e) {
            log.error("根据ID前缀查询反馈失败: prefix={}", idPrefix, e);
        }
        return null;
    }

    /**
     * 管理员回复反馈
     */
    public static boolean reply(String id, String replyContent, boolean isHidden) {
        String sql = "UPDATE `feedback` SET `reply_content` = ?, `reply_time` = ?, `is_hidden` = ?, `is_read` = FALSE WHERE `id` = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, replyContent);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setBoolean(3, isHidden);
            ps.setString(4, id);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                log.info("反馈回复成功: id={}", id);
                return true;
            }
            log.warn("反馈回复失败（可能已被回复或不存在）: id={}", id);
            return false;
        } catch (Exception e) {
            log.error("回复反馈失败: id={}", id, e);
            return false;
        }
    }

    private static int count(String sql) {
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("查询总数失败", e);
        }
        return 0;
    }

    private static List<FeedbackDTO> paginatedQuery(String sql, int page, int pageSize) {
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, (page - 1) * pageSize);
            try (var rs = ps.executeQuery()) {
                List<FeedbackDTO> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(rowToDTO(rs));
                }
                return list;
            }
        } catch (Exception e) {
            log.error("分页查询反馈失败", e);
            return List.of();
        }
    }

    private static List<FeedbackDTO> queryList(String sql) {
        List<FeedbackDTO> list = new ArrayList<>();
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rowToDTO(rs));
            }
        } catch (Exception e) {
            log.error("查询反馈列表失败", e);
        }
        return list;
    }

    private static FeedbackDTO rowToDTO(java.sql.ResultSet rs) throws java.sql.SQLException {
        FeedbackDTO dto = new FeedbackDTO();
        dto.setId(rs.getString("id"));
        dto.setPlatform(rs.getString("platform"));
        dto.setUserId(rs.getString("user_id"));
        dto.setUsername(rs.getString("username"));
        dto.setGroupId(rs.getString("group_id"));
        dto.setSubmitContent(rs.getString("submit_content"));
        dto.setCreateTime(rs.getTimestamp("create_time"));
        dto.setRead(rs.getBoolean("is_read"));
        dto.setReplyContent(rs.getString("reply_content"));
        dto.setReplyTime(rs.getTimestamp("reply_time"));
        dto.setHidden(rs.getBoolean("is_hidden"));
        return dto;
    }
}