package top.yzljc.qqbot.official.permission;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.database.DatabaseManager;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName PermissionGroup
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.permission
 */
@Slf4j
public class PermissionGroup {

    private static final Map<String, PermissionData> cache = new ConcurrentHashMap<>();

    public static void init() {

        String sql = "CREATE TABLE IF NOT EXISTS `permission_group` (" +
                "  `user_openId` VARCHAR(256) NOT NULL," +
                "  `role` TEXT NOT NULL," +
                "  `permissions` TEXT NOT NULL," +
                "  PRIMARY KEY (`user_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.execute();

        } catch (Exception e) {
            log.error("初始化权限组数据库失败: {}", e.getMessage());
        }

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(
                     "SELECT user_openId, role, permissions FROM permission_group")) {

            var rs = stmt.executeQuery();

            while (rs.next()) {

                String userOpenId = rs.getString("user_openId");

                PermissionRole role = PermissionRole.fromString(
                        rs.getString("role")
                );

                String permissionsString = rs.getString("permissions");

                Set<String> permissions = parsePermissions(permissionsString);

                cache.put(userOpenId,
                        new PermissionData(userOpenId, role, permissions));
            }

        } catch (Exception e) {
            log.error("加载权限组缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 获取权限数据
     */
    public static PermissionData getData(String userOpenId) {

        return cache.getOrDefault(userOpenId, new PermissionData(userOpenId, PermissionRole.USER, Set.of())
        );
    }

    /**
     * 是否拥有权限节点
     */
    public static boolean hasPermission(String userOpenId, String permission) {

        PermissionData data = getData(userOpenId);

        return data.permissions().contains("*")
                || data.permissions().contains(permission);
    }

    /**
     * 是否拥有角色
     */
    public static boolean hasRole(String userOpenId, PermissionRole role) {

        return getData(userOpenId).role() == role;
    }

    public static boolean isAdmin(String userOpenId) {

        return getData(userOpenId).role() == PermissionRole.ADMIN || getData(userOpenId).role == PermissionRole.OWNER;
    }

    /**
     * 获取角色
     */
    public static PermissionRole getRole(String userOpenId) {

        return getData(userOpenId).role();
    }

    /**
     * 设置权限组
     */
    public static boolean setPermissionGroup(String userOpenId, PermissionRole role, Set<String> permissions) {

        String permissionsString = String.join(",", permissions);

        String sql = "INSERT INTO permission_group " +
                "(user_openId, role, permissions) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "role = VALUES(role), " +
                "permissions = VALUES(permissions)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userOpenId);
            stmt.setString(2, role.name());
            stmt.setString(3, permissionsString);

            stmt.executeUpdate();

            cache.put(userOpenId,
                    new PermissionData(userOpenId, role, permissions));

            return true;

        } catch (Exception e) {
            log.error("设置权限组失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 添加权限节点
     */
    public static boolean addPermission(String userOpenId,
                                        String permission) {

        PermissionData data = getData(userOpenId);

        Set<String> permissions = ConcurrentHashMap.newKeySet();

        permissions.addAll(data.permissions());

        permissions.add(permission);

        return setPermissionGroup(
                userOpenId,
                data.role(),
                permissions
        );
    }

    /**
     * 删除权限节点
     */
    public static boolean removePermission(String userOpenId,
                                           String permission) {

        PermissionData data = getData(userOpenId);

        Set<String> permissions = ConcurrentHashMap.newKeySet();

        permissions.addAll(data.permissions());

        permissions.remove(permission);

        return setPermissionGroup(
                userOpenId,
                data.role(),
                permissions
        );
    }

    /**
     * 删除整个用户权限数据
     */
    public static boolean removeUser(String userOpenId) {

        String sql = "DELETE FROM permission_group WHERE user_openId = ?";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userOpenId);

            stmt.executeUpdate();

            cache.remove(userOpenId);

            return true;

        } catch (Exception e) {
            log.error("删除用户权限失败: {}", e.getMessage());
            return false;
        }
    }

    private static Set<String> parsePermissions(String permissionsString) {

        if (permissionsString == null || permissionsString.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(permissionsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    public record PermissionData(String userOpenId, PermissionRole role, Set<String> permissions) {
    }
}