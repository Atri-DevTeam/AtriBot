package top.yzljc.atribot.auth.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.repo.C2CRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialUsers
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.atribot.auth.official
 */
@Slf4j
public class OfficialUsers {

    private static final Map<String, UserData> cache = new ConcurrentHashMap<>();

    public static void init() {
        // 建表（含旧表迁移）
        C2CRepository.initTable();

        // 加载缓存
        List<C2CRepository.PermissionRow> rows = C2CRepository.loadAll();
        for (C2CRepository.PermissionRow row : rows) {
            PermissionRole role = PermissionRole.fromString(row.role());
            Set<String> permissions = parsePermissions(row.permissions());
            cache.put(row.userOpenId(), new UserData(row.userOpenId(), role, permissions,
                    row.isBlocked(), row.isIgnored()));
        }
        log.info("OfficialUsers 缓存加载完成，共 {} 条", cache.size());
    }

    /**
     * 获取权限数据（缓存中没有则返回默认值，不写库）
     */
    public static UserData getData(String userOpenId) {
        return cache.getOrDefault(userOpenId, new UserData(userOpenId, PermissionRole.USER, Set.of(), false, false));
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

    // ═══════════════ is_blocked / is_ignored 查询（仅缓存，默认 false） ═══════════════

    /**
     * 查询 is_blocked，仅读缓存，没有就返回 false
     */
    public static boolean isBlocked(String userOpenId) {
        UserData data = cache.get(userOpenId);
        return data != null && data.isBlocked();
    }

    /**
     * 查询 is_ignored，仅读缓存，没有就返回 false
     */
    public static boolean isIgnored(String userOpenId) {
        UserData data = cache.get(userOpenId);
        return data != null && data.isIgnored();
    }

    /**
     * 设置 is_blocked。如果用户不在缓存中则自动补全并写入数据库。
     */
    public static void setBlocked(String userOpenId, boolean blocked) {
        if (C2CRepository.setBlocked(userOpenId, blocked)) {
            UserData existing = cache.get(userOpenId);
            if (existing != null) {
                cache.put(userOpenId, new UserData(userOpenId, existing.role(), existing.permissions(), blocked, existing.isIgnored()));
            } else {
                cache.put(userOpenId, new UserData(userOpenId, PermissionRole.USER, Set.of(), blocked, false));
            }
        }
    }

    /**
     * 设置 is_ignored。如果用户不在缓存中则自动补全并写入数据库。
     */
    public static void setIgnored(String userOpenId, boolean ignored) {
        if (C2CRepository.setIgnored(userOpenId, ignored)) {
            UserData existing = cache.get(userOpenId);
            if (existing != null) {
                cache.put(userOpenId, new UserData(userOpenId, existing.role(), existing.permissions(), existing.isBlocked(), ignored));
            } else {
                cache.put(userOpenId, new UserData(userOpenId, PermissionRole.USER, Set.of(), false, ignored));
            }
        }
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

        UserData existing = cache.get(userOpenId);
        boolean blocked = existing != null && existing.isBlocked();
        boolean ignored = existing != null && existing.isIgnored();

        if (C2CRepository.upsertFull(userOpenId, role.name(), permissionsString, blocked, ignored)) {
            cache.put(userOpenId, new UserData(userOpenId, role, permissions, blocked, ignored));
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
     * 删除整个用户数据
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

    public record UserData(String userOpenId, PermissionRole role, Set<String> permissions,
                           boolean isBlocked, boolean isIgnored) {
    }
}