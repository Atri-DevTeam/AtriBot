package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

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

    public static void initTable() {
        String userSql = "CREATE TABLE IF NOT EXISTS `" + USER_TABLE + "` (" +
                "  `user_openId` VARCHAR(256) NOT NULL," +
                "  `role` TEXT NOT NULL," +
                "  `permissions` TEXT NOT NULL," +
                "  `is_blocked` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `is_ignored` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `c2c_push` BOOLEAN NOT NULL DEFAULT TRUE," +
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
    }

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
     * 删除用户数据
     */
    public static boolean delete(String userOpenId) {
        String sql = "DELETE FROM `" + USER_TABLE + "` WHERE user_openId = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("删除用户数据失败: {}", e.getMessage());
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
