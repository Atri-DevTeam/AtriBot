package top.yzljc.atribot.functions.official.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.repo.GroupRepository;

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
        // 建表
        GroupRepository.initTables();

        // 加载缓存
        List<GroupRepository.GroupRow> rows = GroupRepository.loadAllGroups();
        for (GroupRepository.GroupRow row : rows) {
            cache.put(row.groupOpenId(), new GroupData(
                    row.groupOpenId(), row.opMemberOpenId(), row.timestamp(),
                    row.isWhitelist(), row.isBlacklisted(), row.isAllowedActive(), row.realGroupId()));
        }
        log.info("群白名单缓存加载完成，共 {} 条", cache.size());
    }

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

        if (GroupRepository.insertGroup(groupOpenId, opMemberOpenId, timestamp)) {
            cache.put(groupOpenId, new GroupData(groupOpenId, opMemberOpenId, timestamp, false, false, false, null));
            return true;
        }
        return false;
    }

    /**
     * 删除群数据
     */
    public static boolean removeGroup(String groupOpenId) {
        if (GroupRepository.deleteGroup(groupOpenId)) {
            cache.remove(groupOpenId);
            return true;
        }
        return false;
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

        if (GroupRepository.upsertWhitelist(groupOpenId, oldData.opMemberOpenId(), timestamp, isWhitelist)) {
            cache.put(groupOpenId, new GroupData(groupOpenId, oldData.opMemberOpenId(), timestamp, isWhitelist,
                    oldData.isBlacklisted(), oldData.isAllowedActive(), oldData.realGroupId()));
            return true;
        }
        return false;
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

        if (GroupRepository.upsertRealGroupId(groupOpenId, oldData.opMemberOpenId(), oldData.timestamp(),
                oldData.isWhitelist(), realGroupId)) {
            cache.put(groupOpenId, new GroupData(groupOpenId, oldData.opMemberOpenId(), oldData.timestamp(),
                    oldData.isWhitelist(), oldData.isBlacklisted(), oldData.isAllowedActive(), realGroupId));
            return true;
        }
        return false;
    }

    /**
     * 设置主动推送状态
     */
    public static boolean setAllowedFullMessage(String groupOpenId, boolean allowedActive) {
        GroupData oldData = getData(groupOpenId);

        if (GroupRepository.upsertAllowedFullMessage(groupOpenId, oldData.opMemberOpenId(), oldData.timestamp(),
                oldData.isWhitelist(), allowedActive)) {
            cache.put(groupOpenId, new GroupData(groupOpenId, oldData.opMemberOpenId(), oldData.timestamp(),
                    oldData.isWhitelist(), oldData.isBlacklisted(), allowedActive, oldData.realGroupId()));
            return true;
        }
        return false;
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

        if (GroupRepository.upsertGroupBlacklisted(groupOpenId, oldData.opMemberOpenId(), timestamp,
                oldData.isWhitelist(), isBlacklisted)) {
            cache.put(groupOpenId, new GroupData(groupOpenId, oldData.opMemberOpenId(), timestamp,
                    oldData.isWhitelist(), isBlacklisted, oldData.isAllowedActive(), oldData.realGroupId()));
            return true;
        }
        return false;
    }

    /**
     * 获取群的功能配置 JSON（直接从数据库读取，不使用缓存）
     */
    private static ObjectNode getFunctionConfig(String groupOpenId) {
        String jsonStr = GroupRepository.getFunctionConfigJson(groupOpenId);
        if (jsonStr != null && !jsonStr.isBlank()) {
            try {
                return (ObjectNode) objectMapper.readTree(jsonStr);
            } catch (Exception e) {
                log.error("解析群 {} 的功能配置 JSON 失败: {}", groupOpenId, e.getMessage());
            }
        }
        return objectMapper.createObjectNode();
    }

    /**
     * 保存群的功能配置 JSON 到数据库
     */
    private static boolean saveFunctionConfig(String groupOpenId, ObjectNode config) {
        try {
            String jsonStr = objectMapper.writeValueAsString(config);
            return GroupRepository.saveFunctionConfigJson(groupOpenId, jsonStr);
        } catch (Exception e) {
            log.error("保存群 {} 的功能配置失败: {}", groupOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 查询某个功能在指定群是否开启。
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
     * 获取开启了某功能的所有群列表
     */
    public static List<String> enabledGroups(String functionKey) {
        return GroupRepository.queryEnabledGroups(functionKey);
    }

    /**
     * 获取群的所有功能配置 JSON
     */
    public static ObjectNode getRawFunctionConfig(String groupOpenId) {
        return getFunctionConfig(groupOpenId);
    }

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
