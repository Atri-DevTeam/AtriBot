package top.yzljc.atribot.functions.official.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupWhitelist
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.permission
 */
@Slf4j
public class GroupList {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, GroupData> cache = new ConcurrentHashMap<>();

    public static void init() {

        String sql = "CREATE TABLE IF NOT EXISTS `group_whitelist` (" +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `op_member_openId` VARCHAR(256) NULL," +
                "  `timestamp` BIGINT NOT NULL," +
                "  `is_whitelist` BOOLEAN NOT NULL," +
                "  `is_blacklisted` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `is_allowed_active` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `real_group_id` BIGINT NULL," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.execute();

        } catch (Exception e) {
            log.error("初始化群白名单数据库表失败: {}", e.getMessage());
        }

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(
                     "SELECT group_openId, op_member_openId, timestamp, is_whitelist, is_blacklisted, is_allowed_active, real_group_id FROM group_whitelist")) {

            var rs = stmt.executeQuery();

            while (rs.next()) {

                String groupOpenId = rs.getString("group_openId");
                String opMemberOpenId = rs.getString("op_member_openId");
                long timestamp = rs.getLong("timestamp");
                boolean isWhitelist = rs.getBoolean("is_whitelist");
                boolean isBlacklisted = rs.getBoolean("is_blacklisted");
                boolean isAllowedActive = rs.getBoolean("is_allowed_active");
                Long realGroupId = (Long) rs.getObject("real_group_id");

                cache.put(groupOpenId, new GroupData(groupOpenId, opMemberOpenId, timestamp, isWhitelist, isBlacklisted, isAllowedActive, realGroupId));
            }

        } catch (Exception e) {
            log.error("加载群白名单缓存失败: {}", e.getMessage());
        }

        // ===================== function_list table =====================

        String funcSql = "CREATE TABLE IF NOT EXISTS `function_list` (" +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `functions` JSON NOT NULL," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(funcSql)) {

            stmt.execute();

        } catch (Exception e) {
            log.error("初始化群功能列表数据库表失败: {}", e.getMessage());
        }
    }

    // ===================== Whitelist methods =====================

    /**
     * 注册群
     */
    public static boolean registerGroup(String groupOpenId, String opMemberOpenId, String timestampStr) {

        if (cache.containsKey(groupOpenId)) {
            return true;
        }

        long timestamp;

        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (Exception e) {
            log.error("解析时间戳失败: {}", e.getMessage());
            return false;
        }

        String sql = "INSERT IGNORE INTO group_whitelist " +
                "(group_openId, op_member_openId, timestamp, is_whitelist) " +
                "VALUES (?, ?, ?, ?)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupOpenId);
            stmt.setString(2, opMemberOpenId);
            stmt.setLong(3, timestamp);
            stmt.setBoolean(4, false);

            stmt.executeUpdate();

            cache.put(groupOpenId, new GroupData(groupOpenId, opMemberOpenId, timestamp, false, false, false, null));

            return true;

        } catch (Exception e) {
            log.error("注册群失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 删除群数据
     */
    public static boolean removeGroup(String groupOpenId) {

        String sql = "DELETE FROM group_whitelist WHERE group_openId = ?";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupOpenId);

            stmt.executeUpdate();

            cache.remove(groupOpenId);

            return true;

        } catch (Exception e) {
            log.error("删除群数据失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取群数据
     */
    public static GroupData getData(String groupOpenId) {
        return cache.getOrDefault(groupOpenId, new GroupData(groupOpenId, null, -1, false, false, false, null));
    }

    public static List<GroupData> listGroups() {
        return new ArrayList<>(cache.values());
    }

    /**
     * 是否为白名单群
     */
    public static boolean isWhitelist(String groupOpenId) {
        return getData(groupOpenId).isWhitelist();
    }

    /**
     * 设置白名单状态
     */
    public static boolean setWhitelist(String groupOpenId, boolean isWhitelist) {

        GroupData oldData = getData(groupOpenId);

        long timestamp = System.currentTimeMillis() / 1000;

        String sql = "INSERT INTO group_whitelist " +
                "(group_openId, op_member_openId, timestamp, is_whitelist) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "timestamp = VALUES(timestamp), " +
                "is_whitelist = VALUES(is_whitelist)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupOpenId);
            stmt.setString(2, oldData.opMemberOpenId());
            stmt.setLong(3, timestamp);
            stmt.setBoolean(4, isWhitelist);

            stmt.executeUpdate();

            cache.put(groupOpenId, new GroupData(groupOpenId, oldData.opMemberOpenId(), timestamp, isWhitelist, oldData.isBlacklisted(), oldData.isAllowedActive(), oldData.realGroupId()));

            return true;

        } catch (Exception e) {
            log.error("设置群白名单失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 开启白名单
     */
    public static boolean addWhitelist(String groupOpenId) {
        return setWhitelist(groupOpenId, true);
    }

    /**
     * 关闭白名单
     */
    public static boolean removeWhitelist(String groupOpenId) {
        return setWhitelist(groupOpenId, false);
    }

    /**
     * 获取真实群号
     */
    public static Long getRealGroupId(String groupOpenId) {
        return getData(groupOpenId).realGroupId();
    }

    /**
     * 设置真实群号
     */
    public static boolean setRealGroupId(String groupOpenId, Long realGroupId) {
        GroupData oldData = getData(groupOpenId);

        String sql = "INSERT INTO group_whitelist " +
                "(group_openId, op_member_openId, timestamp, is_whitelist, real_group_id) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE real_group_id = VALUES(real_group_id)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupOpenId);
            stmt.setString(2, oldData.opMemberOpenId());
            stmt.setLong(3, oldData.timestamp());
            stmt.setBoolean(4, oldData.isWhitelist());
            if (realGroupId == null) stmt.setNull(5, java.sql.Types.BIGINT);
            else stmt.setLong(5, realGroupId);
            stmt.executeUpdate();

            cache.put(groupOpenId, new GroupData(groupOpenId, oldData.opMemberOpenId(), oldData.timestamp(),
                    oldData.isWhitelist(), oldData.isBlacklisted(), oldData.isAllowedActive(), realGroupId));
            return true;
        } catch (Exception e) {
            log.error("设置真实群号失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 设置主动推送状态
     */
    public static boolean setAllowedFullMessage(String groupOpenId, boolean allowedActive) {
        GroupData oldData = getData(groupOpenId);

        String sql = "INSERT INTO group_whitelist " +
                "(group_openId, op_member_openId, timestamp, is_whitelist, is_allowed_active) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE is_allowed_active = VALUES(is_allowed_active)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupOpenId);
            stmt.setString(2, oldData.opMemberOpenId());
            stmt.setLong(3, oldData.timestamp());
            stmt.setBoolean(4, oldData.isWhitelist());
            stmt.setBoolean(5, allowedActive);
            stmt.executeUpdate();

            cache.put(groupOpenId, new GroupData(groupOpenId, oldData.opMemberOpenId(), oldData.timestamp(),
                    oldData.isWhitelist(), oldData.isBlacklisted(), allowedActive, oldData.realGroupId()));
            return true;
        } catch (Exception e) {
            log.error("设置主动推送失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 是否允许主动推送（已缓存，启动时加载）
     */
    public static boolean isAllowedFullMessages(String groupOpenId) {
        return getData(groupOpenId).isAllowedActive();
    }

    /**
     * 是否为黑名单群（已缓存，启动时加载）
     */
    public static boolean isGroupBlacklisted(String groupOpenId) {
        return getData(groupOpenId).isBlacklisted();
    }

    /**
     * 设置黑名单状态
     */
    public static boolean setGroupBlacklisted(String groupOpenId, boolean isBlacklisted) {

        GroupData oldData = getData(groupOpenId);

        long timestamp = System.currentTimeMillis() / 1000;

        String sql = "INSERT INTO group_whitelist " +
                "(group_openId, op_member_openId, timestamp, is_whitelist, is_blacklisted) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "is_blacklisted = VALUES(is_blacklisted)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupOpenId);
            stmt.setString(2, oldData.opMemberOpenId());
            stmt.setLong(3, timestamp);
            stmt.setBoolean(4, oldData.isWhitelist());
            stmt.setBoolean(5, isBlacklisted);

            stmt.executeUpdate();

            cache.put(groupOpenId, new GroupData(groupOpenId, oldData.opMemberOpenId(), timestamp, oldData.isWhitelist(), isBlacklisted, oldData.isAllowedActive(), oldData.realGroupId()));

            return true;

        } catch (Exception e) {
            log.error("设置群黑名单失败: {}", e.getMessage());
            return false;
        }
    }

    // ===================== Function list methods =====================

    /**
     * 获取群的功能配置 JSON（直接从数据库读取，不使用缓存）
     */
    private static ObjectNode getFunctionConfig(String groupOpenId) {
        String sql = "SELECT functions FROM function_list WHERE group_openId = ?";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupOpenId);
            var rs = stmt.executeQuery();

            if (rs.next()) {
                String jsonStr = rs.getString("functions");
                if (jsonStr != null && !jsonStr.isBlank()) {
                    return (ObjectNode) objectMapper.readTree(jsonStr);
                }
            }

        } catch (Exception e) {
            log.error("读取群 {} 的功能配置失败: {}", groupOpenId, e.getMessage());
        }

        return objectMapper.createObjectNode();
    }

    /**
     * 保存群的功能配置 JSON 到数据库
     */
    private static boolean saveFunctionConfig(String groupOpenId, ObjectNode config) {
        String sql = "INSERT INTO function_list (group_openId, functions) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE functions = VALUES(functions)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            String jsonStr = objectMapper.writeValueAsString(config);
            stmt.setString(1, groupOpenId);
            stmt.setString(2, jsonStr);

            stmt.executeUpdate();
            return true;

        } catch (Exception e) {
            log.error("保存群 {} 的功能配置失败: {}", groupOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 查询某个功能在指定群是否开启。
     * 规则：如果 functionKey 不存在于 JSON 中，默认 false（关闭）。
     * 如果存在但 enabled = false，也返回 false。
     * 只有 enabled = true 才返回 true。
     */
    public static boolean isFunctionEnabled(String groupOpenId, String functionKey) {
        ObjectNode config = getFunctionConfig(groupOpenId);

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
     * 设置某个功能在指定群的启用状态。
     *
     * @param groupOpenId 群 openId
     * @param functionKey 功能键名
     * @param enabled     是否启用
     * @param operator    操作者 openId
     */
    public static boolean setFunctionEnabled(String groupOpenId, String functionKey, boolean enabled, String operator) {
        ObjectNode config = getFunctionConfig(groupOpenId);

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

        return saveFunctionConfig(groupOpenId, config);
    }

    /**
     * 获取开启了某功能的所有群列表（直接从数据库读取，不使用缓存）。
     * 使用 MySQL JSON_EXTRACT 查询。
     *
     * @param functionKey 功能键名
     * @return 启用了该功能的群 openId 列表
     */
    public static List<String> enabledGroups(String functionKey) {
        List<String> groups = new ArrayList<>();

        String sql = "SELECT group_openId FROM function_list " +
                "WHERE JSON_EXTRACT(functions, ?) = true";

        String jsonPath = "$." + functionKey + ".enabled";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jsonPath);
            var rs = stmt.executeQuery();

            while (rs.next()) {
                groups.add(rs.getString("group_openId"));
            }

        } catch (Exception e) {
            log.error("查询功能 {} 的启用群列表失败: {}", functionKey, e.getMessage());
        }

        return groups;
    }

    /**
     * 获取某个功能在指定群的完整信息。
     *
     * @return FunctionInfo，若功能不存在则 enabled 为 false
     */
    public static FunctionInfo getFunctionInfo(String groupOpenId, String functionKey) {
        ObjectNode config = getFunctionConfig(groupOpenId);

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

    public record GroupData(String groupOpenId, String opMemberOpenId, long timestamp, boolean isWhitelist, boolean isBlacklisted, boolean isAllowedActive, Long realGroupId) {
    }

    public record FunctionInfo(boolean enabled, String operator, String time) {
    }
}
