package top.yzljc.atribot.auth.official;

import top.yzljc.atricore.auth.official.*;
import top.yzljc.atricore.auth.AccountStatus;
import top.yzljc.atribot.auth.UnifiedAuthentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.repo.C2CRepository;
import top.yzljc.atribot.database.repo.BotUserDataRepository;
import top.yzljc.atribot.function.tasks.pushtask.PushTaskGlobalSettings;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    public static final String BILIBILI_UID_SETTING = "bv_uid";
    private static final String UNSUPPORTED_KEYBOARD_SETTING = "unsupported_keyboard";
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, UserData> cache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> unsupportedKeyboardCache = new ConcurrentHashMap<>();

    public static void init() {
        unsupportedKeyboardCache.clear();
        C2CRepository.initTable();
        cache.clear();
        for (var row : C2CRepository.loadAll()) {
            cache.put(row.userOpenId(), new UserData(row.userOpenId(), row.c2cPush()));
        }
        log.info("OfficialUsers 缓存加载完成，共 {} 条", cache.size());
    }

    /**
     * 获取权限数据（缓存中没有则返回默认值，不写库）
     */
    public static UserData getData(String userOpenId) {
        return cache.getOrDefault(userOpenId, new UserData(userOpenId, true));
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
     * 注册私聊用户并补全统一账号，已有权限与状态保持不变
     *
     * @param userOpenId QQ 用户 OpenID
     */
    public static void registerUser(String userOpenId) {
        if (UnifiedAuthentication.ensureByQqUserOpenId(userOpenId, null) != null && C2CRepository.registerUser(userOpenId)) {
            cache.putIfAbsent(userOpenId, new UserData(userOpenId, true));
        }
    }

    /**
     * 从统一账号缓存判断用户是否处于拉黑状态
     *
     * @param userOpenId QQ 用户 OpenID
     * @return 状态为 SUSPENDED 时返回 true
     */
    public static boolean isBlocked(String userOpenId) {
        return UnifiedAuthentication.getQqUserStatus(userOpenId) == AccountStatus.SUSPENDED;
    }

    /**
     * 从统一账号缓存判断是否应静默忽略用户交互，已注销账号同样忽略
     *
     * @param userOpenId QQ 用户 OpenID
     * @return 状态为 BANNED 或 DELETED 时返回 true
     */
    public static boolean isIgnored(String userOpenId) {
        AccountStatus status = UnifiedAuthentication.getQqUserStatus(userOpenId);
        return status == AccountStatus.BANNED || status == AccountStatus.DELETED;
    }

    /**
     * 设置统一账号的拉黑状态，解除时仅恢复当前处于拉黑状态的账号
     *
     * @param userOpenId QQ 用户 OpenID
     * @param blocked 是否拉黑
     * @return 状态保存成功时返回 true
     */
    public static boolean setBlocked(String userOpenId, boolean blocked) {
        return UnifiedAuthentication.setQqUserStatus(userOpenId,
                blocked ? AccountStatus.SUSPENDED : AccountStatus.ACTIVE, blocked ? null : AccountStatus.SUSPENDED);
    }

    /**
     * 设置统一账号的屏蔽状态，解除时仅恢复当前处于屏蔽状态的账号
     *
     * @param userOpenId QQ 用户 OpenID
     * @param ignored 是否屏蔽
     * @return 状态保存成功时返回 true
     */
    public static boolean setIgnored(String userOpenId, boolean ignored) {
        return UnifiedAuthentication.setQqUserStatus(userOpenId,
                ignored ? AccountStatus.BANNED : AccountStatus.ACTIVE, ignored ? null : AccountStatus.BANNED);
    }

    /**
     * 查询私聊主动消息授权状态，默认开启
     */
    public static boolean isC2CPushEnabled(String userOpenId) {
        return getData(userOpenId).c2cPush();
    }

    /**
     * 更新已有私聊用户的主动消息授权，不创建私聊记录
     */
    public static void setC2CPush(String userOpenId, boolean c2cPush) {
        if (C2CRepository.setC2CPush(userOpenId, c2cPush)) {
            cache.computeIfPresent(userOpenId, (id, existing) -> new UserData(id, c2cPush));
        }
    }

    /**
     * 是否拥有权限节点
     */
    public static boolean hasPermission(String userOpenId, String permission) {
        var account = UnifiedAuthentication.getCachedQqUserAccount(userOpenId);
        return account != null && (account.permissions().contains("*") || account.permissions().contains(permission));
    }

    /**
     * 是否拥有角色
     */
    public static boolean hasRole(String userOpenId, UnifiedRole role) {
        return getRole(userOpenId) == role;
    }

    public static boolean isAdmin(String userOpenId) {
        UnifiedRole role = getRole(userOpenId);
        return role == UnifiedRole.ADMIN || role == UnifiedRole.OWNER;
    }

    /**
     * 获取角色
     */
    public static UnifiedRole getRole(String userOpenId) {
        var account = UnifiedAuthentication.getCachedQqUserAccount(userOpenId);
        return account == null ? UnifiedRole.USER : account.role();
    }

    /**
     * 设置权限组
     */
    public static boolean setPermissionGroup(String userOpenId, UnifiedRole role, Set<String> permissions) {
        if (userOpenId == null || userOpenId.isBlank()) return false;
        Set<String> savedPermissions = Set.copyOf(permissions);
        if (!UnifiedAuthentication.saveOfficialUserPermissions(userOpenId, role, savedPermissions)) return false;
        return true;
    }

    /**
     * 保存统一账号权限、状态及已有私聊授权，仅补全统一账号，不创建私聊记录
     *
     * @param userOpenId QQ 用户 OpenID
     * @param role 统一账号权限组
     * @param permissions 统一账号权限节点
     * @param status 统一账号状态
     * @param c2cPush 私聊主动消息设置
     * @return 所有字段保存成功时返回 true
     */
    public static boolean setProfile(String userOpenId, UnifiedRole role, Set<String> permissions,
                                     AccountStatus status, boolean c2cPush) {
        if (userOpenId == null || userOpenId.isBlank() || status == null) return false;
        Set<String> savedPermissions = Set.copyOf(permissions);
        if (UnifiedAuthentication.saveOfficialUserProfile(userOpenId, role, savedPermissions, status, c2cPush)) {
            cache.computeIfPresent(userOpenId, (id, existing) -> new UserData(id, c2cPush));
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
     * 移除私聊记录、功能订阅及对应好友缓存，保留统一账号及其他业务数据
     *
     * @param userOpenId QQ 用户 OpenID
     * @return 私聊记录删除成功时返回 true
     */
    public static boolean removeUser(String userOpenId) {
        if (userOpenId == null || userOpenId.isBlank()) {
            log.warn("拒绝删除用户数据：用户 OpenID 为空");
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
        String jsonStr = BotUserDataRepository.getFunctionConfigJson(userOpenId);
        if (jsonStr != null && !jsonStr.isBlank()) {
            try {
                return (ObjectNode) objectMapper.readTree(jsonStr);
            } catch (Exception e) {
                log.error("解析私聊用户 {} 的功能配置 JSON 失败: {}", userOpenId, e.getMessage());
            }
        }
        return objectMapper.createObjectNode();
    }

    /** 读取私聊用户的功能配置，用于展示订阅状态。 */
    public static ObjectNode getRawFunctionConfig(String userOpenId) {
        return getFunctionConfig(userOpenId);
    }

    /**
     * 保存私聊用户的功能配置 JSON 到数据库
     */
    private static boolean saveFunctionConfig(String userOpenId, ObjectNode config) {
        try {
            String jsonStr = objectMapper.writeValueAsString(config);
            return BotUserDataRepository.saveFunctionConfigJson(userOpenId, jsonStr);
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
        return BotUserDataRepository.queryEnabledUsers(functionKey);
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

    /**
     * 读取用户个人偏好设置中的某个字段，不存在返回 null。
     * 返回 JsonNode，可通过 asText()/asInt()/asBoolean() 等取值。
     */
    public static JsonNode getUserSetting(String userOpenId, String setting) {
        String jsonStr = BotUserDataRepository.getUserSettingsJson(userOpenId);
        if (jsonStr == null || jsonStr.isBlank()) {
            return null;
        }
        try {
            JsonNode settings = objectMapper.readTree(jsonStr);
            if (settings.isObject() && settings.has(setting)) {
                return settings.get(setting);
            }
        } catch (Exception e) {
            log.error("读取用户 {} 的偏好设置 {} 失败: {}", userOpenId, setting, e.getMessage());
        }
        return null;
    }

    /**
     * 设置用户个人偏好设置中的某个字段，data 可为任意可被 Jackson 序列化的类型
     * （字符串、数字、布尔、对象、数组等）。原有其他字段不受影响
     */
    public static boolean setUserSetting(String userOpenId, String setting, Object data) {
        try {
            boolean saved = BotUserDataRepository.setUserSetting(userOpenId, setting, objectMapper.writeValueAsString(data),
                    BILIBILI_UID_SETTING.equals(setting)) == BotUserDataRepository.SettingWriteResult.SAVED;
            if (saved && UNSUPPORTED_KEYBOARD_SETTING.equals(setting)) {
                // 写入成功后失效，下一次查询重新加载，兼容通用设置入口及并发写入
                unsupportedKeyboardCache.remove(userOpenId);
            }
            return saved;
        } catch (Exception e) {
            log.error("保存用户 {} 的偏好设置失败: {}", userOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 按用户懒加载键盘兼容设置，默认为 {@code false}
     */
    public static boolean isUserUnsupportedKeyboard(String userOpenId) {
        if (userOpenId == null || userOpenId.isBlank()) {
            return false;
        }
        return unsupportedKeyboardCache.computeIfAbsent(userOpenId, id -> {
            var d = getUserSetting(id, UNSUPPORTED_KEYBOARD_SETTING);
            return d != null && d.asBoolean(false);
        });
    }

    public static boolean setUserUnsupportedKeyboard(String userOpenId, boolean unsupported) {
        return setUserSetting(userOpenId, UNSUPPORTED_KEYBOARD_SETTING, unsupported);
    }

    /**
     * 关注校验通过后调用；一个用户只能首次绑定，同一个 UID 只能绑定一个用户。
     */
    public static BotUserDataRepository.SettingWriteResult bindBilibiliUid(String userOpenId, long uid) {
        if (uid <= 0) return BotUserDataRepository.SettingWriteResult.FAILED;
        return BotUserDataRepository.setUserSetting(userOpenId, BILIBILI_UID_SETTING, Long.toString(uid), true);
    }

    public record UserData(String userOpenId, boolean c2cPush) {
        public UnifiedRole role() {
            var account = UnifiedAuthentication.getCachedQqUserAccount(userOpenId);
            return account == null ? UnifiedRole.USER : account.role();
        }

        public Set<String> permissions() {
            var account = UnifiedAuthentication.getCachedQqUserAccount(userOpenId);
            return account == null ? Set.of() : Set.copyOf(account.permissions());
        }

        public AccountStatus status() {
            return UnifiedAuthentication.getQqUserStatus(userOpenId);
        }
    }

    public record FunctionInfo(boolean enabled, String operator, String time) {
    }
}
