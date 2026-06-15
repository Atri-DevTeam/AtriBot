package top.yzljc.atribot.functions.official.permission;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.repo.C2CRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName PermissionGroup
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.permission
 */
@Slf4j
public class C2CList {

    private static final Map<String, UserData> cache = new ConcurrentHashMap<>();

    public static void init() {
        // 建表
        C2CRepository.initTable();

        // 加载缓存
        List<C2CRepository.PermissionRow> rows = C2CRepository.loadAll();
        for (C2CRepository.PermissionRow row : rows) {
            PermissionRole role = PermissionRole.fromString(row.role());
            Set<String> permissions = parsePermissions(row.permissions());
            cache.put(row.userOpenId(), new UserData(row.userOpenId(), role, permissions));
        }
        log.info("C2C 权限缓存加载完成，共 {} 条", cache.size());
    }

    /**
     * 获取权限数据
     */
    public static UserData getData(String userOpenId) {
        return cache.getOrDefault(userOpenId, new UserData(userOpenId, PermissionRole.USER, Set.of()));
    }

    /**
     * 列出所有已缓存的用户
     */
    public static List<UserData> listAll() {
        return new ArrayList<>(cache.values());
    }

    /**
     * 缓存中是否已存在该用户
     */
    public static boolean isCached(String userOpenId) {
        return cache.containsKey(userOpenId);
    }

    /**
     * 注册 C2C 用户到缓存和数据库（默认 USER 角色，空权限）
     */
    public static void registerUser(String userOpenId) {
        setPermissionGroup(userOpenId, PermissionRole.USER, Set.of());
    }

    /**
     * 是否拥有权限节点
     */
    public static boolean hasPermission(String userOpenId, String permission) {
        UserData data = getData(userOpenId);
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

        if (C2CRepository.upsert(userOpenId, role.name(), permissionsString)) {
            cache.put(userOpenId, new UserData(userOpenId, role, permissions));
            return true;
        }
        return false;
    }

    /**
     * 添加权限节点
     */
    public static boolean addPermission(String userOpenId, String permission) {
        UserData data = getData(userOpenId);
        Set<String> permissions = ConcurrentHashMap.newKeySet();
        permissions.addAll(data.permissions());
        permissions.add(permission);
        return setPermissionGroup(userOpenId, data.role(), permissions);
    }

    /**
     * 删除权限节点
     */
    public static boolean removePermission(String userOpenId, String permission) {
        UserData data = getData(userOpenId);
        Set<String> permissions = ConcurrentHashMap.newKeySet();
        permissions.addAll(data.permissions());
        permissions.remove(permission);
        return setPermissionGroup(userOpenId, data.role(), permissions);
    }

    /**
     * 删除整个用户权限数据
     */
    public static boolean removeUser(String userOpenId) {
        if (C2CRepository.delete(userOpenId)) {
            cache.remove(userOpenId);
            return true;
        }
        return false;
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

    public record UserData(String userOpenId, PermissionRole role, Set<String> permissions) {
    }
}