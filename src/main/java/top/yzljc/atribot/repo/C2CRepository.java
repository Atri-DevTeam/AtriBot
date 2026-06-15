package top.yzljc.atribot.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.util.ArrayList;
import java.util.List;

/**
 * permission_group 表的纯数据库访问层。
 * 缓存和业务逻辑保留在 C2CList 中。
 *
 * @Author YZ_Ljc_
 * @ClassName C2CRepository
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.repo
 */
@Slf4j
public class C2CRepository {

    // ==================== Table init ====================

    public static void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `permission_group` (" +
                "  `user_openId` VARCHAR(256) NOT NULL," +
                "  `role` TEXT NOT NULL," +
                "  `permissions` TEXT NOT NULL," +
                "  PRIMARY KEY (`user_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
        } catch (Exception e) {
            log.error("初始化权限组数据库失败: {}", e.getMessage());
        }
    }

    // ==================== CRUD ====================

    /**
     * 加载所有权限数据（用于启动时填充缓存）
     */
    public static List<PermissionRow> loadAll() {
        List<PermissionRow> rows = new ArrayList<>();
        String sql = "SELECT user_openId, role, permissions FROM permission_group";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {

            while (rs.next()) {
                rows.add(new PermissionRow(
                        rs.getString("user_openId"),
                        rs.getString("role"),
                        rs.getString("permissions")
                ));
            }
        } catch (Exception e) {
            log.error("加载权限组缓存失败: {}", e.getMessage());
        }
        return rows;
    }

    /**
     * 插入或更新权限
     */
    public static boolean upsert(String userOpenId, String role, String permissions) {
        String sql = "INSERT INTO permission_group (user_openId, role, permissions) VALUES (?, ?, ?) " +
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
     * 删除用户权限数据
     */
    public static boolean delete(String userOpenId) {
        String sql = "DELETE FROM permission_group WHERE user_openId = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("删除用户权限失败: {}", e.getMessage());
            return false;
        }
    }

    public record PermissionRow(String userOpenId, String role, String permissions) {
    }
}
