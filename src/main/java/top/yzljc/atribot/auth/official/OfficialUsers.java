package top.yzljc.atribot.auth.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.repo.C2CRepository;
import top.yzljc.atribot.function.tasks.pushtask.PushTaskGlobalSettings;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, UserData> cache = new ConcurrentHashMap<>();

    public static void init() {
        // 建表（含旧表迁移）
        C2CRepository.initTable();

        // 加载缓存
        List<C2CRepository.PermissionRow> rows = C2CRepository.loadAll();
        for (C2CRepository.PermissionRow row : rows) {
            UnifiedRole role = UnifiedRole.fromString(row.role());
            Set<String> permissions = parsePermissions(row.permissions());
            cache.put(row.userOpenId(), new UserData(row.userOpenId(), role, permissions,
                    row.isBlocked(), row.isIgnored(), row.c2cPush()));
        }
        log.info("OfficialUsers 缓存加载完成，共 {} 条", cache.size());
    }

    /**
     * 获取权限数据（缓存中没有则返回默认值，不写库）
     */
    public static UserData getData(String userOpenId) {
        return cache.getOrDefault(userOpenId, new UserData(userOpenId, UnifiedRole.USER, Set.of(), false, false, true));
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
        setPermissionGroup(userOpenId, UnifiedRole.USER, Set.of());
    }

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
     * 设置 is_blocked。如果用户不在缓存中则自动补全并写入数据库
     */
    public static void setBlocked(String userOpenId, boolean blocked) {
        if (C2CRepository.setBlocked(userOpenId, blocked)) {
            UserData existing = cache.get(userOpenId);
            if (existing != null) {
                cache.put(userOpenId, new UserData(userOpenId, existing.role(), existing.permissions(), blocked, existing.isIgnored(), existing.c2cPush()));
            } else {
                cache.put(userOpenId, new UserData(userOpenId, UnifiedRole.USER, Set.of(), blocked, false, true));
            }
        }
    }

    /**
     * 设置 is_ignored。如果用户不在缓存中则自动补全并写入数据库
     */
    public static void setIgnored(String userOpenId, boolean ignored) {
        if (C2CRepository.setIgnored(userOpenId, ignored)) {
            UserData existing = cache.get(userOpenId);
            if (existing != null) {
                cache.put(userOpenId, new UserData(userOpenId, existing.role(), existing.permissions(), existing.isBlocked(), ignored, existing.c2cPush()));
            } else {
                cache.put(userOpenId, new UserData(userOpenId, UnifiedRole.USER, Set.of(), false, ignored, true));
            }
        }
    }

    /**
     * 查询私聊主动消息授权状态，默认开启
     */
    public static boolean isC2CPushEnabled(String userOpenId) {
        return getData(userOpenId).c2cPush();
    }

    /**
     * 设置私聊主动消息授权状态。如果用户不在缓存中则自动补全并写入数据库
     */
    public static void setC2CPush(String userOpenId, boolean c2cPush) {
        if (C2CRepository.setC2CPush(userOpenId, c2cPush)) {
            UserData existing = cache.get(userOpenId);
            if (existing != null) {
                cache.put(userOpenId, new UserData(userOpenId, existing.role(), existing.permissions(),
                        existing.isBlocked(), existing.isIgnored(), c2cPush));
            } else {
                cache.put(userOpenId, new UserData(userOpenId, UnifiedRole.USER, Set.of(), false, false, c2cPush));
            }
        }
    }

    /**
     * 是否拥有权限节点
     */
    public static boolean hasPermission(String userOpenId, String permission) {
        UserData data = getData(userOpenId);
        return data.permissions().contains("*") || data.permissions().contains(permission);
    }

    /**
     * 是否拥有角色
     */
    public static boolean hasRole(String userOpenId, UnifiedRole role) {
        return getData(userOpenId).role() == role;
    }

    public static boolean isAdmin(String userOpenId) {
        return getData(userOpenId).role() == UnifiedRole.ADMIN || getData(userOpenId).role == UnifiedRole.OWNER;
    }

    /**
     * 获取角色
     */
    public static UnifiedRole getRole(String userOpenId) {
        return getData(userOpenId).role();
    }

    /**
     * 设置权限组
     */
    public static boolean setPermissionGroup(String userOpenId, UnifiedRole role, Set<String> permissions) {
        String permissionsString = String.join(",", permissions);

        UserData existing = cache.get(userOpenId);
        boolean blocked = existing != null && existing.isBlocked();
        boolean ignored = existing != null && existing.isIgnored();
        boolean c2cPush = existing == null || existing.c2cPush();

        if (C2CRepository.upsertFull(userOpenId, role.name(), permissionsString, blocked, ignored, c2cPush)) {
            cache.put(userOpenId, new UserData(userOpenId, role, permissions, blocked, ignored, c2cPush));
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
        if (isIgnored(userOpenId) || isBlocked(userOpenId)) {
            log.warn("用户 {} 已被拉黑，保留相关数据不再清除", userOpenId);
            return false;
        }
        if (C2CRepository.delete(userOpenId)) {
            cache.remove(userOpenId);
            return true;
        }
        return false;
    }

    /**
     * 获取私聊用户的功能配置 JSON（直接从数据库读取，不使用缓存）。
     */
    private static ObjectNode getFunctionConfig(String userOpenId) {
        String jsonStr = C2CRepository.getFunctionConfigJson(userOpenId);
        if (jsonStr != null && !jsonStr.isBlank()) {
            try {
                return (ObjectNode) objectMapper.readTree(jsonStr);
            } catch (Exception e) {
                log.error("解析私聊用户 {} 的功能配置 JSON 失败: {}", userOpenId, e.getMessage());
            }
        }
        return objectMapper.createObjectNode();
    }

    /**
     * 保存私聊用户的功能配置 JSON 到数据库
     */
    private static boolean saveFunctionConfig(String userOpenId, ObjectNode config) {
        try {
            String jsonStr = objectMapper.writeValueAsString(config);
            return C2CRepository.saveFunctionConfigJson(userOpenId, jsonStr);
        } catch (Exception e) {
            log.error("保存私聊用户 {} 的功能配置失败: {}", userOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 查询某个功能在指定私聊用户是否开启
     */
    public static boolean isFunctionEnabled(String userOpenId, String functionKey) {
        ObjectNode config = getFunctionConfig(userOpenId);

        if (!config.has(functionKey)) {
            return false;
        }

        JsonNode funcNode = config.get(functionKey);
        if (funcNode == null || !funcNode.isObject()) {
            return false;
        }

        JsonNode enabledNode = funcNode.get("enabled");
        if (enabledNode == null || !enabledNode.isBoolean()) {
            return false;
        }

        return enabledNode.asBoolean();
    }

    /**
     * 设置某个功能在指定私聊用户的启用状态。
     */
    public static boolean setFunctionEnabled(String userOpenId, String functionKey, boolean enabled, String operator) {
        ObjectNode config = getFunctionConfig(userOpenId);

        ObjectNode funcNode;
        if (config.has(functionKey) && config.get(functionKey).isObject()) {
            funcNode = (ObjectNode) config.get(functionKey);
        } else {
            funcNode = objectMapper.createObjectNode();
        }

        funcNode.put("enabled", enabled);
        funcNode.put("operator", operator);
        funcNode.put("time", LocalDateTime.now().format(dtf));

        config.set(functionKey, funcNode);

        return saveFunctionConfig(userOpenId, config);
    }

    /**
     * 获取开启了某功能的所有私聊用户列表
     */
    public static List<String> enabledUsers(String functionKey) {
        if (PushTaskGlobalSettings.isDisabledForC2C(functionKey)) {
            return List.of();
        }
        return C2CRepository.queryEnabledUsers(functionKey);
    }

    public static FunctionInfo getFunctionInfo(String userOpenId, String functionKey) {
        ObjectNode config = getFunctionConfig(userOpenId);

        if (!config.has(functionKey)) {
            return new FunctionInfo(false, null, null);
        }

        JsonNode funcNode = config.get(functionKey);
        if (funcNode == null || !funcNode.isObject()) {
            return new FunctionInfo(false, null, null);
        }

        boolean enabled = funcNode.has("enabled") && funcNode.get("enabled").asBoolean();
        String operator = funcNode.has("operator") ? funcNode.get("operator").asText() : null;
        String time = funcNode.has("time") ? funcNode.get("time").asText() : null;
        return new FunctionInfo(enabled, operator, time);
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

    public record UserData(String userOpenId, UnifiedRole role, Set<String> permissions,
                           boolean isBlocked, boolean isIgnored, boolean c2cPush) {
    }

    public record FunctionInfo(boolean enabled, String operator, String time) {
    }
}
