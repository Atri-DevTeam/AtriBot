package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.ImageReviewStatus;
import top.yzljc.atribot.database.ImageSourceDTO;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 图源投稿数据库访问层
 *
 * @Author YZ_Ljc_
 * @ClassName ImageSourceRepository
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class ImageSourceRepository {

    // ==================== Table init ====================

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `image_source` (" +
                "  `id` VARCHAR(36) NOT NULL," +
                "  `image_uuid` VARCHAR(64) NULL," +
                "  `platform` VARCHAR(32) NOT NULL," +
                "  `uploader_id` VARCHAR(255) NOT NULL," +
                "  `uploader_name` VARCHAR(255) NULL," +
                "  `group_id` VARCHAR(255) NULL," +
                "  `source_url` TEXT NULL," +
                "  `file_name` VARCHAR(255) NULL," +
                "  `content_type` VARCHAR(64) NULL," +
                "  `width` INT NOT NULL DEFAULT 0," +
                "  `height` INT NOT NULL DEFAULT 0," +
                "  `file_size` BIGINT NOT NULL DEFAULT 0," +
                "  `hash` VARCHAR(128) NULL," +
                "  `review_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'," +
                "  `reviewer` VARCHAR(255) NULL," +
                "  `review_remark` TEXT NULL," +
                "  `review_time` DATETIME NULL," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  `is_notified` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_image_uuid` (`image_uuid`)," +
                "  INDEX `idx_uploader_id` (`uploader_id`)," +
                "  INDEX `idx_review_status` (`review_status`)," +
                "  INDEX `idx_hash` (`hash`)," +
                "  INDEX `idx_create_time` (`create_time`)," +
                "  INDEX `idx_notify` (`uploader_id`, `is_notified`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
            log.info("图源数据库表初始化完成");
        } catch (Exception e) {
            log.error("初始化图源数据库表失败", e);
        }
    }

    // ==================== 投稿 ====================

    /**
     * 写入一条投稿记录，返回生成的主键 UUID；失败返回 null。
     */
    public static String insert(ImageSourceDTO dto) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO `image_source` (`id`, `image_uuid`, `platform`, `uploader_id`, `uploader_name`, " +
                "`group_id`, `source_url`, `file_name`, `content_type`, `width`, `height`, `file_size`, `hash`, " +
                "`review_status`, `create_time`, `is_notified`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, dto.getImageUuid());
            ps.setString(3, dto.getPlatform());
            ps.setString(4, dto.getUploaderId());
            ps.setString(5, dto.getUploaderName());
            ps.setString(6, dto.getGroupId());
            ps.setString(7, dto.getSourceUrl());
            ps.setString(8, dto.getFileName());
            ps.setString(9, dto.getContentType());
            ps.setInt(10, dto.getWidth());
            ps.setInt(11, dto.getHeight());
            ps.setLong(12, dto.getFileSize());
            ps.setString(13, dto.getHash());
            ps.setString(14, dto.getReviewStatus() != null ? dto.getReviewStatus() : ImageReviewStatus.PENDING.name());
            ps.setTimestamp(15, dto.getCreateTime() != null ? dto.getCreateTime() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            log.info("图源投稿写入成功: id={}, imageUuid={}, uploader={}", id, dto.getImageUuid(), dto.getUploaderId());
            return id;
        } catch (Exception e) {
            log.error("写入图源投稿失败: uploader={}", dto.getUploaderId(), e);
            return null;
        }
    }

    /**
     * 按内容 hash 查重，命中返回已存在的记录。
     */
    public static ImageSourceDTO findByHash(String hash) {
        if (hash == null || hash.isBlank()) return null;
        return querySingle("SELECT * FROM `image_source` WHERE `hash` = ? LIMIT 1", hash);
    }

    public static ImageSourceDTO findById(String id) {
        return querySingle("SELECT * FROM `image_source` WHERE `id` = ? LIMIT 1", id);
    }

    public static ImageSourceDTO findByIdPrefix(String idPrefix) {
        if (idPrefix == null || idPrefix.isBlank()) return null;
        return querySingle("SELECT * FROM `image_source` WHERE `id` LIKE ? LIMIT 1", idPrefix + "%");
    }

    public static ImageSourceDTO findByImageUuid(String imageUuid) {
        if (imageUuid == null || imageUuid.isBlank()) return null;
        return querySingle("SELECT * FROM `image_source` WHERE `image_uuid` = ? LIMIT 1", imageUuid);
    }

    /**
     * 统计用户当前处于未审核状态的投稿数，用于限流。
     */
    public static int countPendingByUploader(String uploaderId) {
        String sql = "SELECT COUNT(*) FROM `image_source` WHERE `uploader_id` = ? AND `review_status` = 'PENDING'";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, uploaderId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("统计用户待审核投稿失败: uploader={}", uploaderId, e);
        }
        return 0;
    }

    // ==================== 审核 ====================

    /**
     * 审核一条投稿。置为终态的同时把 is_notified 复位，交由通知层重新投递结果。
     *
     * @param status 目标状态，应为 {@link ImageReviewStatus} 之一
     * @return 是否命中并更新了记录
     */
    public static boolean review(String id, ImageReviewStatus status, String reviewer, String remark) {
        String sql = "UPDATE `image_source` SET `review_status` = ?, `reviewer` = ?, `review_remark` = ?, " +
                "`review_time` = ?, `is_notified` = FALSE WHERE `id` = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, reviewer);
            ps.setString(3, remark);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.setString(5, id);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                log.info("图源审核完成: id={}, status={}, reviewer={}", id, status.name(), reviewer);
                return true;
            }
            log.warn("图源审核未命中记录: id={}", id);
            return false;
        } catch (Exception e) {
            log.error("图源审核失败: id={}", id, e);
            return false;
        }
    }

    public static boolean delete(String id) {
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement("DELETE FROM `image_source` WHERE `id` = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("删除图源记录失败: id={}", id, e);
            return false;
        }
    }

    // ==================== 通知投递 ====================

    /**
     * 取出该用户一条已审核但结果尚未送达的投稿。
     */
    public static ImageSourceDTO findPendingNotifyByUploader(String uploaderId) {
        if (uploaderId == null || uploaderId.isBlank()) return null;
        String sql = "SELECT * FROM `image_source` WHERE `uploader_id` = ? AND `is_notified` = FALSE " +
                "AND `review_status` <> 'PENDING' ORDER BY `review_time` ASC LIMIT 1";
        return querySingle(sql, uploaderId);
    }

    /**
     * 取出所有待送达的审核结果，供主动推送批量消费。
     */
    public static List<ImageSourceDTO> findAllPendingNotify(int limit) {
        String sql = "SELECT * FROM `image_source` WHERE `is_notified` = FALSE AND `review_status` <> 'PENDING' " +
                "ORDER BY `review_time` ASC LIMIT ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                List<ImageSourceDTO> list = new ArrayList<>();
                while (rs.next()) list.add(rowToDTO(rs));
                return list;
            }
        } catch (Exception e) {
            log.error("查询待送达图源审核结果失败", e);
            return List.of();
        }
    }

    public static boolean markNotified(String id) {
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement("UPDATE `image_source` SET `is_notified` = TRUE WHERE `id` = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("标记图源结果已送达失败: id={}", id, e);
            return false;
        }
    }

    // ==================== 列表查询 ====================

    /**
     * 按状态分页查询；status 传 null 表示全部。
     */
    public static List<ImageSourceDTO> findPaginated(String status, int page, int pageSize) {
        boolean filtered = status != null && !status.isBlank();
        String sql = filtered
                ? "SELECT * FROM `image_source` WHERE `review_status` = ? ORDER BY `create_time` DESC LIMIT ? OFFSET ?"
                : "SELECT * FROM `image_source` ORDER BY `create_time` DESC LIMIT ? OFFSET ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            int idx = 1;
            if (filtered) ps.setString(idx++, status);
            ps.setInt(idx++, pageSize);
            ps.setInt(idx, Math.max(0, (page - 1) * pageSize));
            try (var rs = ps.executeQuery()) {
                List<ImageSourceDTO> list = new ArrayList<>();
                while (rs.next()) list.add(rowToDTO(rs));
                return list;
            }
        } catch (Exception e) {
            log.error("分页查询图源失败: status={}", status, e);
            return List.of();
        }
    }

    /**
     * 统计某位投稿人的投稿数；status 传 null 表示全部状态。
     */
    public static int countByUploader(String uploaderId, String status) {
        boolean filtered = status != null && !status.isBlank();
        String sql = filtered
                ? "SELECT COUNT(*) FROM `image_source` WHERE `uploader_id` = ? AND `review_status` = ?"
                : "SELECT COUNT(*) FROM `image_source` WHERE `uploader_id` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, uploaderId);
            if (filtered) ps.setString(2, status);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("统计用户投稿数量失败: uploader={}", uploaderId, e);
        }
        return 0;
    }

    /**
     * 最近一段时间内的投稿数，用于观察投稿热度。
     */
    public static int countSince(long millisAgo) {
        String sql = "SELECT COUNT(*) FROM `image_source` WHERE `create_time` >= ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - millisAgo));
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("统计近期投稿数量失败", e);
        }
        return 0;
    }

    public static int countByStatus(String status) {
        boolean filtered = status != null && !status.isBlank();
        String sql = filtered
                ? "SELECT COUNT(*) FROM `image_source` WHERE `review_status` = ?"
                : "SELECT COUNT(*) FROM `image_source`";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            if (filtered) ps.setString(1, status);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("统计图源数量失败: status={}", status, e);
        }
        return 0;
    }

    // ==================== 内部工具 ====================

    private static ImageSourceDTO querySingle(String sql, String param) {
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, param);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rowToDTO(rs);
            }
        } catch (Exception e) {
            log.error("查询图源失败: param={}", param, e);
        }
        return null;
    }

    private static ImageSourceDTO rowToDTO(java.sql.ResultSet rs) throws java.sql.SQLException {
        ImageSourceDTO dto = new ImageSourceDTO();
        dto.setId(rs.getString("id"));
        dto.setImageUuid(rs.getString("image_uuid"));
        dto.setPlatform(rs.getString("platform"));
        dto.setUploaderId(rs.getString("uploader_id"));
        dto.setUploaderName(rs.getString("uploader_name"));
        dto.setGroupId(rs.getString("group_id"));
        dto.setSourceUrl(rs.getString("source_url"));
        dto.setFileName(rs.getString("file_name"));
        dto.setContentType(rs.getString("content_type"));
        dto.setWidth(rs.getInt("width"));
        dto.setHeight(rs.getInt("height"));
        dto.setFileSize(rs.getLong("file_size"));
        dto.setHash(rs.getString("hash"));
        dto.setReviewStatus(rs.getString("review_status"));
        dto.setReviewer(rs.getString("reviewer"));
        dto.setReviewRemark(rs.getString("review_remark"));
        dto.setReviewTime(rs.getTimestamp("review_time"));
        dto.setCreateTime(rs.getTimestamp("create_time"));
        dto.setNotified(rs.getBoolean("is_notified"));
        return dto;
    }
}
