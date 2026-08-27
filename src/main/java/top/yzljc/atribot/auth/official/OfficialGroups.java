package top.yzljc.atribot.auth.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.repo.GroupRepository;
import top.yzljc.atribot.function.tasks.pushtask.PushTaskGlobalSettings;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.qq.GroupProfile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
public class OfficialGroups {

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
                    row.groupOpenId(), row.opMemberOpenId(), row.joinedAt(),
                    row.isWhitelist(), row.isBlacklisted(), row.allowProactiveMsg(), row.realGroupId(),
                    row.memberOpenid(), parseScope(row.recvMsgSetting()), parseRole(row.memberRole()),
                    row.groupName(), row.groupFingerMemo(), row.groupClassText(),
                    parseGroupTags(row.groupTagsJson()), row.groupMemberNum()));
        }
        log.info("群白名单缓存加载完成，共 {} 条", cache.size());
    }

    public static boolean registerGroup(String groupOpenId, String opMemberOpenId, String joinedAt) {

        if (cache.containsKey(groupOpenId)) {
            return true;
        }

        if (GroupRepository.insertGroup(groupOpenId, opMemberOpenId, joinedAt)) {
            cache.put(groupOpenId, new GroupData(groupOpenId, opMemberOpenId, joinedAt, false, false, false,
                    null, null, null, null, null, null, null, List.of(), 0));
            return true;
        }
        return false;
    }

    /**
     * 删除群数据
     */
    public static boolean removeGroup(String groupOpenId) {
        if (isGroupBlacklisted(groupOpenId)) return false; // 黑名单群不允许删除
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
        return cache.getOrDefault(groupOpenId, new GroupData(groupOpenId, null, null, false, false, false,
                null, null, null, null, null, null, null, List.of(), 0));
    }

    public static boolean isCached(String groupOpenId) {
        return groupOpenId != null && cache.containsKey(groupOpenId);
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

        if (GroupRepository.upsertWhitelist(groupOpenId, oldData.opMemberOpenId(), oldData.joinedAt(), isWhitelist)) {
            cache.put(groupOpenId, oldData.withWhitelist(isWhitelist));
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

        if (GroupRepository.upsertRealGroupId(groupOpenId, oldData.opMemberOpenId(), oldData.joinedAt(),
                oldData.isWhitelist(), realGroupId)) {
            cache.put(groupOpenId, oldData.withRealGroupId(realGroupId));
            return true;
        }
        return false;
    }

    /**
     * 设置主动推送状态
     */
    public static boolean setAllowProactiveMsg(String groupOpenId, boolean allowProactiveMsg) {
        GroupData oldData = getData(groupOpenId);

        if (GroupRepository.upsertAllowProactiveMsg(groupOpenId, oldData.opMemberOpenId(), oldData.joinedAt(),
                oldData.isWhitelist(), allowProactiveMsg)) {
            cache.put(groupOpenId, oldData.withAllowProactiveMsg(allowProactiveMsg));
            return true;
        }
        return false;
    }

    /**
     * 是否允许主动推送（已缓存，启动时加载）
     */
    public static boolean allowProactiveMsg(String groupOpenId) {
        return getData(groupOpenId).allowProactiveMsg();
    }

    /**
     * 是否为黑名单群（已缓存，启动时加载）
     */
    public static boolean isGroupBlacklisted(String groupOpenId) {
        if (groupOpenId == null) return false;
        return getData(groupOpenId).isBlacklisted();
    }

    /**
     * 设置黑名单状态
     */
    public static boolean setGroupBlacklisted(String groupOpenId, boolean isBlacklisted) {
        GroupData oldData = getData(groupOpenId);

        if (GroupRepository.upsertGroupBlacklisted(groupOpenId, oldData.opMemberOpenId(), oldData.joinedAt(),
                oldData.isWhitelist(), isBlacklisted)) {
            cache.put(groupOpenId, oldData.withBlacklisted(isBlacklisted));
            return true;
        }
        return false;
    }

    public static boolean saveGroupProfile(GroupProfile profile) {
        if (profile == null || profile.groupId() == null || profile.groupId().isBlank()) {
            return false;
        }

        GroupData oldData = getData(profile.groupId());
        List<String> groupTags = profile.groupTags() == null ? List.of() : List.copyOf(profile.groupTags());
        String groupTagsJson;
        try {
            groupTagsJson = objectMapper.writeValueAsString(groupTags);
        } catch (Exception e) {
            log.error("序列化群 {} 标签失败: {}", profile.groupId(), e.getMessage());
            return false;
        }

        if (GroupRepository.upsertGroupProfile(
                profile.groupId(),
                oldData.opMemberOpenId(),
                profile.joinTime(),
                oldData.isWhitelist(),
                oldData.isBlacklisted(),
                profile.allowProactiveMsg(),
                oldData.realGroupId(),
                profile.memberOpenId(),
                profile.receiveMsgSetting() == null ? null : profile.receiveMsgSetting().getJsonValue(),
                profile.memberRole() == null ? null : profile.memberRole().name().toLowerCase(Locale.ROOT),
                profile.groupName(),
                profile.groupFingerMemo(),
                profile.groupClassText(),
                groupTagsJson,
                profile.groupMemberNum())) {
            cache.put(profile.groupId(), new GroupData(
                    profile.groupId(),
                    oldData.opMemberOpenId(),
                    profile.joinTime(),
                    oldData.isWhitelist(),
                    oldData.isBlacklisted(),
                    profile.allowProactiveMsg(),
                    oldData.realGroupId(),
                    profile.memberOpenId(),
                    profile.receiveMsgSetting(),
                    profile.memberRole(),
                    profile.groupName(),
                    profile.groupFingerMemo(),
                    profile.groupClassText(),
                    groupTags,
                    profile.groupMemberNum()));
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
        if (PushTaskGlobalSettings.isDisabledForGroup(functionKey)) {
            return List.of();
        }
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

    private static GroupProfile.Scope parseScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return GroupProfile.Scope.from(value);
    }

    private static PlatformRole parseRole(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PlatformRole.valueOf(value);
        } catch (IllegalArgumentException e) {
            return PlatformRole.getPlatformRole(value);
        }
    }

    private static List<String> parseGroupTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> tags = new ArrayList<>();
            for (JsonNode tagNode : node) {
                if (tagNode != null && !tagNode.isNull()) {
                    tags.add(tagNode.asText());
                }
            }
            return Collections.unmodifiableList(tags);
        } catch (Exception e) {
            log.warn("解析群标签失败: {}", e.getMessage());
            return List.of();
        }
    }

    public record GroupData(String groupOpenId, String opMemberOpenId, String joinedAt,
                            boolean isWhitelist, boolean isBlacklisted, boolean allowProactiveMsg,
                            Long realGroupId, String memberOpenid, GroupProfile.Scope recvMsgSetting,
                            PlatformRole memberRole, String groupName, String groupFingerMemo,
                            String groupClassText, List<String> groupTags, int groupMemberNum) {

        public GroupData withWhitelist(boolean whitelist) {
            return new GroupData(groupOpenId, opMemberOpenId, joinedAt, whitelist, isBlacklisted, allowProactiveMsg,
                    realGroupId, memberOpenid, recvMsgSetting, memberRole, groupName, groupFingerMemo,
                    groupClassText, groupTags, groupMemberNum);
        }

        public GroupData withBlacklisted(boolean blacklisted) {
            return new GroupData(groupOpenId, opMemberOpenId, joinedAt, isWhitelist, blacklisted, allowProactiveMsg,
                    realGroupId, memberOpenid, recvMsgSetting, memberRole, groupName, groupFingerMemo,
                    groupClassText, groupTags, groupMemberNum);
        }

        public GroupData withAllowProactiveMsg(boolean proactiveMsg) {
            return new GroupData(groupOpenId, opMemberOpenId, joinedAt, isWhitelist, isBlacklisted, proactiveMsg,
                    realGroupId, memberOpenid, recvMsgSetting, memberRole, groupName, groupFingerMemo,
                    groupClassText, groupTags, groupMemberNum);
        }

        public GroupData withRealGroupId(Long groupId) {
            return new GroupData(groupOpenId, opMemberOpenId, joinedAt, isWhitelist, isBlacklisted, allowProactiveMsg,
                    groupId, memberOpenid, recvMsgSetting, memberRole, groupName, groupFingerMemo,
                    groupClassText, groupTags, groupMemberNum);
        }
    }

    public record FunctionInfo(boolean enabled, String operator, String time) {
    }
}
