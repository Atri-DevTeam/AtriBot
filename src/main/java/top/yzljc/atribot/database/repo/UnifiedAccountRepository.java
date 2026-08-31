package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.AccountStatus;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.UnifiedAccountDTO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName UnifiedAccountRepository
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class UnifiedAccountRepository {

    private static final String TABLE = "unified_account";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `" + TABLE + "` (" +
                "  `uuid` CHAR(36) NOT NULL," +
                "  `username` VARCHAR(64) NULL," +
                "  `qq_user_open_id` VARCHAR(128) NULL," +
                "  `qq_user_uin` VARCHAR(64) NULL," +
                "  `minecraft_uuid` VARCHAR(64) NULL," +
                "  `role` VARCHAR(32) NOT NULL DEFAULT 'USER'," +
                "  `permissions` TEXT NULL," +
                "  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  `last_update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`uuid`)," +
                "  UNIQUE KEY `uk_qq_user_open_id` (`qq_user_open_id`)," +
                "  UNIQUE KEY `uk_qq_user_uin` (`qq_user_uin`)," +
                "  KEY `idx_username` (`username`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection()) {
            try (var ps = con.prepareStatement(sql)) {
                ps.execute();
            }
            // 同一个正版 Minecraft 账号允许绑定多个统一账号。
            try (var ps = con.prepareStatement("ALTER TABLE `" + TABLE + "` DROP INDEX `uk_minecraft_uuid`")) {
                ps.execute();
            } catch (SQLException ignored) {
                // 新表没有该索引，或数据库已完成迁移。
            }
            log.info("统一账号表初始化完成");
        } catch (Exception e) {
            log.error("初始化统一账号表失败", e);
        }
    }

    // ==================== 创建 ====================

    /**
     * 创建统一账号。uuid 与 create_time 自动生成，last_update_time 初始等于 create_time。
     * 标识字段（openId / uin / mcUuid）唯一，冲突时返回 null。
     */
    public static UnifiedAccountDTO create(String username, String qqUserOpenId, String qqUserUin,
                                           String minecraftUuid, UnifiedRole role, List<String> permissions,
                                           AccountStatus status) {
        UUID uuid = UUID.randomUUID();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String roleName = role == null ? UnifiedRole.USER.name() : role.name();
        String statusName = status == null ? AccountStatus.ACTIVE.name() : status.name();
        String permissionsJson = toJson(permissions);

        String sql = "INSERT INTO `" + TABLE + "` (`uuid`, `username`, `qq_user_open_id`, `qq_user_uin`, " +
                "`minecraft_uuid`, `role`, `permissions`, `status`, `create_time`, `last_update_time`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            setNullableString(ps, 3, qqUserOpenId);
            setNullableString(ps, 4, qqUserUin);
            setNullableString(ps, 5, minecraftUuid);
            ps.setString(6, roleName);
            setNullableString(ps, 7, permissionsJson);
            ps.setString(8, statusName);
            ps.setTimestamp(9, now);
            ps.setTimestamp(10, now);
            ps.executeUpdate();
            return new UnifiedAccountDTO(uuid, username, qqUserOpenId, qqUserUin, minecraftUuid, roleName,
                    permissions, statusName, now, now);
        } catch (Exception e) {
            log.error("创建统一账号失败: openId={}, uin={}, mcUuid={}", qqUserOpenId, qqUserUin, minecraftUuid, e);
            return null;
        }
    }

    // ==================== 查询 ====================

    public static UnifiedAccountDTO findByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return findByColumn("uuid", uuid.toString());
    }

    public static UnifiedAccountDTO findByQqUserOpenId(String openId) {
        return findByColumn("qq_user_open_id", openId);
    }

    public static UnifiedAccountDTO findByQqUserUin(String uin) {
        return findByColumn("qq_user_uin", uin);
    }

    public static UnifiedAccountDTO findByMinecraftUuid(String mcUuid) {
        return findByColumn("minecraft_uuid", mcUuid);
    }

    /**
     * 按用户名查询（可能多个，按创建时间倒序）。
     */
    public static List<UnifiedAccountDTO> findByUsername(String username) {
        String sql = "SELECT * FROM `" + TABLE + "` WHERE `username` = ? ORDER BY `create_time` DESC";
        List<UnifiedAccountDTO> result = new ArrayList<>();
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToDTO(rs));
                }
            }
        } catch (Exception e) {
            log.error("按用户名查询统一账号失败: username={}", username, e);
        }
        return result;
    }

    public static List<UnifiedAccountDTO> findAll() {
        String sql = "SELECT * FROM `" + TABLE + "` ORDER BY `create_time` DESC";
        List<UnifiedAccountDTO> result = new ArrayList<>();
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rowToDTO(rs));
            }
        } catch (Exception e) {
            log.error("查询全部统一账号失败", e);
        }
        return result;
    }

    public static int count() {
        String sql = "SELECT COUNT(*) FROM `" + TABLE + "`";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("统计统一账号失败", e);
        }
        return 0;
    }

    // ==================== 修改（各字段独立，自动刷新 last_update_time） ====================

    public static boolean updateUsername(UUID uuid, String username) {
        return updateField(uuid, "username", username);
    }

    public static boolean updateQqUserOpenId(UUID uuid, String openId) {
        return updateField(uuid, "qq_user_open_id", openId);
    }

    public static boolean updateQqUserUin(UUID uuid, String uin) {
        return updateField(uuid, "qq_user_uin", uin);
    }

    public static boolean updateMinecraftUuid(UUID uuid, String mcUuid) {
        return updateField(uuid, "minecraft_uuid", mcUuid);
    }

    public static boolean updateRole(UUID uuid, UnifiedRole role) {
        return updateField(uuid, "role", role == null ? UnifiedRole.USER.name() : role.name());
    }

    public static boolean updateStatus(UUID uuid, AccountStatus status) {
        return updateField(uuid, "status", status == null ? AccountStatus.ACTIVE.name() : status.name());
    }

    public static boolean updatePermissions(UUID uuid, List<String> permissions) {
        return updateField(uuid, "permissions", toJson(permissions));
    }

    // ==================== 删除 ====================

    public static boolean delete(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        String sql = "DELETE FROM `" + TABLE + "` WHERE `uuid` = ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("删除统一账号失败: uuid={}", uuid, e);
            return false;
        }
    }

    // ==================== 私有辅助 ====================

    /**
     * 按单个字符串列查询，命中返回首条（列已建唯一索引，理论上最多一条）。
     */
    private static UnifiedAccountDTO findByColumn(String column, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM `" + TABLE + "` WHERE `" + column + "` = ? LIMIT 1";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, value);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToDTO(rs);
                }
            }
        } catch (Exception e) {
            log.error("按 {} 查询统一账号失败: {}", column, value, e);
        }
        return null;
    }

    /**
     * 更新单个字段并自动刷新 last_update_time。column 为内部白名单列名，非用户输入。
     */
    private static boolean updateField(UUID uuid, String column, String value) {
        if (uuid == null) {
            return false;
        }
        String sql = "UPDATE `" + TABLE + "` SET `" + column + "` = ?, `last_update_time` = ? WHERE `uuid` = ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            setNullableString(ps, 1, value);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("更新统一账号字段失败: uuid={}, column={}", uuid, column, e);
            return false;
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static UnifiedAccountDTO rowToDTO(ResultSet rs) throws SQLException {
        return new UnifiedAccountDTO(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("qq_user_open_id"),
                rs.getString("qq_user_uin"),
                rs.getString("minecraft_uuid"),
                rs.getString("role"),
                parseJson(rs.getString("permissions")),
                rs.getString("status"),
                rs.getTimestamp("create_time"),
                rs.getTimestamp("last_update_time")
        );
    }

    /**
     * 权限列表序列化为 JSON 字符串存列，null / 空列表返回 null 由 setNullableString 归一为 NULL。
     */
    private static String toJson(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (Exception e) {
            log.error("序列化权限列表失败", e);
            return null;
        }
    }

    /**
     * 列 JSON 反序列化回权限列表，空 / 非法返回空列表。
     */
    private static List<String> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.error("反序列化权限列表失败: {}", json, e);
            return List.of();
        }
    }
}
