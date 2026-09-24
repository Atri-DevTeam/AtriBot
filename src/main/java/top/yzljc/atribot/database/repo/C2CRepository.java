package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName C2CRepository
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.repo
 */
@Slf4j
public class C2CRepository {

    private static final String USER_TABLE = "official_users";
    private static final String C2C_FUNCTION_TABLE = "c2c_function_list";
    private static final ObjectMapper JSON = new ObjectMapper();

    public enum SettingWriteResult { SAVED, ALREADY_EXISTS, UID_IN_USE, FAILED }

    /** 在同一个行锁事务内读写单个设置，避免并发更新其他偏好时丢失绑定记录。 */
    public static SettingWriteResult setUserSetting(String userOpenId, String setting, String valueJson, boolean onlyIfAbsent) {
        if (userOpenId == null || userOpenId.isBlank() || setting == null || setting.isBlank()) return SettingWriteResult.FAILED;
        try (var connection = DatabaseManager.getConnection()) {
            return setUserSetting(connection, userOpenId, setting, valueJson, onlyIfAbsent);
        } catch (Exception e) {
            log.error("保存用户 {} 的设置 {} 失败", userOpenId, setting, e);
            return SettingWriteResult.FAILED;
        }
    }

    static SettingWriteResult setUserSetting(Connection connection, String userOpenId, String setting,
                                              String valueJson, boolean onlyIfAbsent) throws Exception {
        if ("bv_uid".equals(setting)) {
            var uid = JSON.readTree(valueJson);
            if (uid == null || !uid.isIntegralNumber() || !uid.canConvertToLong() || uid.longValue() <= 0) {
                return SettingWriteResult.FAILED;
            }
            // MySQL 命名锁跨连接/实例生效，同一个 UID 的检查与提交必须串行。
            String lockName = "atrimeow:bv_uid:" + uid.longValue();
            try (var ps = connection.prepareStatement("SELECT GET_LOCK(?, 10)")) {
                ps.setString(1, lockName);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt(1) != 1) throw new SQLException("获取 B站 UID 绑定锁失败");
                }
            }
            try {
                return writeUserSetting(connection, userOpenId, setting, valueJson, true, uid.longValue());
            } finally {
                try (var ps = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    ps.setString(1, lockName);
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next() || rs.getInt(1) != 1) throw new SQLException("释放 B站 UID 绑定锁失败");
                    }
                } catch (Exception releaseError) {
                    // 连接池复用前必须释放命名锁；释放失败则关闭物理连接。
                    try { connection.abort(Runnable::run); }
                    catch (Exception abortError) { releaseError.addSuppressed(abortError); }
                    throw releaseError;
                }
            }
        }
        return writeUserSetting(connection, userOpenId, setting, valueJson, onlyIfAbsent, null);
    }

    private static SettingWriteResult writeUserSetting(Connection connection, String userOpenId, String setting,
                                                       String valueJson, boolean onlyIfAbsent, Long bilibiliUid) throws Exception {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            // 先确保用户行存在，并获取写锁；同一用户的并发首次绑定也会串行执行。
            try (var ps = connection.prepareStatement("INSERT INTO `" + USER_TABLE
                    + "` (user_openId, role, permissions) VALUES (?, 'USER', '')"
                    + " ON DUPLICATE KEY UPDATE user_openId = user_openId")) {
                ps.setString(1, userOpenId);
                ps.executeUpdate();
            }
            ObjectNode settings;
            try (var ps = connection.prepareStatement("SELECT user_settings FROM `" + USER_TABLE + "` WHERE user_openId = ? FOR UPDATE")) {
                ps.setString(1, userOpenId);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("用户设置行不存在");
                    String raw = rs.getString(1);
                    var node = raw == null || raw.isBlank() ? JSON.createObjectNode() : JSON.readTree(raw);
                    if (!(node instanceof ObjectNode object)) throw new SQLException("用户设置不是 JSON 对象，拒绝覆盖");
                    settings = object;
                }
            }
            if (onlyIfAbsent && settings.hasNonNull(setting)) {
                connection.rollback();
                return SettingWriteResult.ALREADY_EXISTS;
            }
            if (bilibiliUid != null) {
                // 也查询升级前已有的 JSON 记录，数字和字符串形式的 UID 均视为占用。
                // 持有 UID 命名锁期间使用当前事务的首次一致性读取，无需锁住其他用户行。
                try (var ps = connection.prepareStatement("SELECT user_openId FROM `" + USER_TABLE
                        + "` WHERE user_openId <> ? AND CAST(JSON_UNQUOTE(JSON_EXTRACT(user_settings, '$.bv_uid'))"
                        + " AS DECIMAL(20, 0)) = ? LIMIT 1")) {
                    ps.setString(1, userOpenId);
                    ps.setLong(2, bilibiliUid);
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            return SettingWriteResult.UID_IN_USE;
                        }
                    }
                }
            }
            settings.set(setting, JSON.readTree(valueJson));
            try (var ps = connection.prepareStatement("UPDATE `" + USER_TABLE + "` SET user_settings = ? WHERE user_openId = ?")) {
                ps.setString(1, settings.toString());
                ps.setString(2, userOpenId);
                ps.executeUpdate();
            }
            connection.commit();
            return SettingWriteResult.SAVED;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public static void initTable() {
        String userSql = "CREATE TABLE IF NOT EXISTS `" + USER_TABLE + "` (" +
                "  `user_openId` VARCHAR(256) NOT NULL," +
                "  `role` TEXT NOT NULL," +
                "  `permissions` TEXT NOT NULL," +
                "  `is_blocked` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `is_ignored` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `c2c_push` BOOLEAN NOT NULL DEFAULT TRUE," +
                "  `user_settings` JSON NULL," +
                "  `game_data` JSON NULL," +
                "  PRIMARY KEY (`user_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String functionSql = "CREATE TABLE IF NOT EXISTS `" + C2C_FUNCTION_TABLE + "` (" +
                "  `user_openId` VARCHAR(256) NOT NULL," +
                "  `functions` JSON NOT NULL," +
                "  PRIMARY KEY (`user_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection()) {
            try (var ps = con.prepareStatement(userSql)) {
                ps.execute();
            }
            try (var ps = con.prepareStatement(functionSql)) {
                ps.execute();
            }
        } catch (Exception e) {
            log.error("初始化 official_users/c2c_function_list 表失败: {}", e.getMessage());
        }
//        migrateGameDataColumn();
    }

//    private static void migrateGameDataColumn() {
//        try (var con = DatabaseManager.getConnection();
//             var ps = con.prepareStatement("ALTER TABLE `official_users` ADD COLUMN `game_data` JSON NULL")) {
//            ps.executeUpdate();
//            log.info("official_users 表已扩列 game_data");
//        } catch (SQLException e) {
//            if (e.getErrorCode() != 1060) throw new IllegalStateException("扩列 game_data 失败", e);
//        }
//    }

//    /**
//     * 旧表迁移：为 official_users 补充 user_settings 列（JSON，存个人偏好设置），列已存在则忽略
//     */
//    private static void migrateUserSettingsColumn() {
//        try (var con = DatabaseManager.getConnection();
//             var ps = con.prepareStatement(
//                     "ALTER TABLE `" + USER_TABLE + "` ADD COLUMN `user_settings` JSON NULL")) {
//            ps.execute();
//            log.info("official_users 表已扩列 user_settings");
//        } catch (SQLException ignored) {
//            // 列已存在，忽略
//        } catch (Exception e) {
//            log.error("official_users 表扩列 user_settings 失败: {}", e.getMessage());
//        }
//    }

    /**
     * 加载所有数据（用于启动时填充缓存）
     */
    public static List<PermissionRow> loadAll() {
        List<PermissionRow> rows = new ArrayList<>();
        String sql = "SELECT user_openId, role, permissions, is_blocked, is_ignored, c2c_push FROM `" + USER_TABLE + "`";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {

            while (rs.next()) {
                rows.add(new PermissionRow(
                        rs.getString("user_openId"),
                        rs.getString("role"),
                        rs.getString("permissions"),
                        rs.getBoolean("is_blocked"),
                        rs.getBoolean("is_ignored"),
                        rs.getBoolean("c2c_push")
                ));
            }
        } catch (Exception e) {
            log.error("加载 official_users 数据失败: {}", e.getMessage());
        }
        return rows;
    }

    /**
     * 插入或更新权限组（不含 blocked/ignored）
     */
    public static boolean upsert(String userOpenId, String role, String permissions) {
        String sql = "INSERT INTO `" + USER_TABLE + "` (user_openId, role, permissions) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE role = VALUES(role), permissions = VALUES(permissions)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setString(2, role);
            ps.setString(3, permissions);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("设置权限组失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 插入或更新全部字段（含 blocked/ignored）
     */
    public static boolean upsertFull(String userOpenId, String role, String permissions,
                                     boolean isBlocked, boolean isIgnored, boolean c2cPush) {
        String sql = "INSERT INTO `" + USER_TABLE + "` (user_openId, role, permissions, is_blocked, is_ignored, c2c_push) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE role = VALUES(role), permissions = VALUES(permissions), " +
                "is_blocked = VALUES(is_blocked), is_ignored = VALUES(is_ignored), c2c_push = VALUES(c2c_push)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setString(2, role);
            ps.setString(3, permissions);
            ps.setBoolean(4, isBlocked);
            ps.setBoolean(5, isIgnored);
            ps.setBoolean(6, c2cPush);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("upsertFull 失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 单独更新 is_blocked
     */
    public static boolean setBlocked(String userOpenId, boolean blocked) {
        String sql = "INSERT INTO `" + USER_TABLE + "` (user_openId, role, permissions, is_blocked) " +
                "VALUES (?, 'USER', '', ?) " +
                "ON DUPLICATE KEY UPDATE is_blocked = VALUES(is_blocked)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setBoolean(2, blocked);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("setBlocked 失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 单独更新 is_ignored
     */
    public static boolean setIgnored(String userOpenId, boolean ignored) {
        String sql = "INSERT INTO `" + USER_TABLE + "` (user_openId, role, permissions, is_ignored) " +
                "VALUES (?, 'USER', '', ?) " +
                "ON DUPLICATE KEY UPDATE is_ignored = VALUES(is_ignored)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setBoolean(2, ignored);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("setIgnored 失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 单独更新 c2c_push
     */
    public static boolean setC2CPush(String userOpenId, boolean c2cPush) {
        String sql = "INSERT INTO `" + USER_TABLE + "` (user_openId, role, permissions, c2c_push) " +
                "VALUES (?, 'USER', '', ?) " +
                "ON DUPLICATE KEY UPDATE c2c_push = VALUES(c2c_push)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setBoolean(2, c2cPush);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("setC2CPush 失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 在同一事务中删除用户资料及私聊功能配置。
     */
    public static boolean delete(String userOpenId) {
        if (userOpenId == null || userOpenId.isBlank()) {
            log.warn("拒绝删除用户数据：用户 OpenID 为空");
            return false;
        }
        String sql = "DELETE FROM `" + USER_TABLE + "` WHERE user_openId = ?";
        String functionSql = "DELETE FROM `" + C2C_FUNCTION_TABLE + "` WHERE user_openId = ?";

        try (var con = DatabaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                int userRows;
                int functionRows;
                try (var ps = con.prepareStatement(sql)) {
                    ps.setString(1, userOpenId);
                    userRows = ps.executeUpdate();
                }
                try (var ps = con.prepareStatement(functionSql)) {
                    ps.setString(1, userOpenId);
                    functionRows = ps.executeUpdate();
                }
                con.commit();
                log.info("清理私聊用户 {} 完成：用户资料 {} 行，功能配置 {} 行", userOpenId, userRows, functionRows);
                return true;
            } catch (SQLException e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        } catch (Exception e) {
            log.error("删除私聊用户 {} 的资料及功能配置失败", userOpenId, e);
            return false;
        }
    }

    /**
     * 读取用户个人偏好设置 JSON 字符串，未设置返回 null。
     */
    public static String getUserSettingsJson(String userOpenId) {
        String sql = "SELECT user_settings FROM `" + USER_TABLE + "` WHERE user_openId = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("user_settings");
            }
        } catch (Exception e) {
            log.error("读取用户 {} 的个人偏好设置失败: {}", userOpenId, e.getMessage());
        }
        return null;
    }

    /**
     * 保存用户个人偏好设置 JSON 字符串（用户不存在时插入默认权限行）。
     */
    public static boolean saveUserSettingsJson(String userOpenId, String json) {
        String sql = "INSERT INTO `" + USER_TABLE + "` (user_openId, role, permissions, user_settings) " +
                "VALUES (?, 'USER', '', ?) " +
                "ON DUPLICATE KEY UPDATE user_settings = VALUES(user_settings)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setString(2, json);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("保存用户 {} 的个人偏好设置失败: {}", userOpenId, e.getMessage());
            return false;
        }
    }

    // ==================== c2c_function_list CRUD ====================

    /**
     * 读取私聊的 c2c_function_list JSON 字符串，不存在返回 null。
     */
    public static String getFunctionConfigJson(String userOpenId) {
        String sql = "SELECT functions FROM `" + C2C_FUNCTION_TABLE + "` WHERE user_openId = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("functions");
            }
        } catch (Exception e) {
            log.error("读取私聊用户 {} 的功能配置失败: {}", userOpenId, e.getMessage());
        }
        return null;
    }

    /**
     * 保存私聊的 c2c_function_list JSON 字符串。
     */
    public static boolean saveFunctionConfigJson(String userOpenId, String json) {
        String sql = "INSERT INTO `" + C2C_FUNCTION_TABLE + "` (user_openId, functions) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE functions = VALUES(functions)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setString(2, json);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("保存私聊用户 {} 的功能配置失败: {}", userOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 查询开启某功能的所有私聊用户 openId。
     */
    public static List<String> queryEnabledUsers(String functionKey) {
        List<String> users = new ArrayList<>();
        String sql = "SELECT user_openId FROM `" + C2C_FUNCTION_TABLE + "` WHERE JSON_EXTRACT(functions, ?) = true";
        String jsonPath = "$." + functionKey + ".enabled";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, jsonPath);
            var rs = ps.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("user_openId"));
            }
        } catch (Exception e) {
            log.error("查询功能 {} 的启用私聊用户列表失败: {}", functionKey, e.getMessage());
        }
        return users;
    }

    public record PermissionRow(String userOpenId, String role, String permissions,
                                boolean isBlocked, boolean isIgnored, boolean c2cPush) {
    }
}
