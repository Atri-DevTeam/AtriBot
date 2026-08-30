package top.yzljc.atribot.chat.official.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.sakuraba_ema.groups.GroupBotRegistry;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Mute
 * @Created_at 2026/08/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 * @Description 群成员禁言管理
 *
 * 机器人需拥有群管理员身份，单次最多设置 10 个成员，接口频率限制 60 QPM
 */
@Slf4j
public final class Mute {

    private static final int MAX_MEMBERS_PER_CALL = 10;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 禁言操作类型：add 增加禁言，update 更新禁言到期时间，del 解除禁言 */
    public enum Op {
        ADD("add"),
        UPDATE("update"),
        DEL("del");

        private final String value;

        Op(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** 单条禁言设置 */
    public record MuteItem(Op op, String memberOpenId, String muteExpireAt) {
        /** 不解禁/不改期时（如仅 del）可省略到期时间 */
        public MuteItem(Op op, String memberOpenId) {
            this(op, memberOpenId, null);
        }
    }

    private static String getUrl(String groupId) {
        String apiBaseUrl = GroupBotRegistry.find(groupId)
                .map(client -> client.getConfig().apiBaseUrl())
                .orElseGet(() -> Config.getInstance().getQqApiBaseUrl());
        return apiBaseUrl + "/v2/groups/" + groupId + "/restrict_chat_setting";
    }

    private static String authHeader(String groupId) {
        String token = GroupBotRegistry.find(groupId)
                .map(client -> client.getTokenManager().getAccessToken())
                .orElseGet(() -> Atri.getInstance().getTokenManager().getAccessToken());
        return "QQBot " + token;
    }

    /**
     * 禁言单个群成员
     *
     * @param groupOpenId  群 OpenID
     * @param memberOpenId 被禁言成员的 OpenID（只能是普通成员，不能是群主/管理员/机器人）
     * @param duration     禁言时长，从当前时间起算，为空则按接口默认处理
     * @return 是否成功
     */
    public static boolean muteMember(String groupOpenId, String memberOpenId, Duration duration) {
        return setGroupMemberMute(groupOpenId, List.of(new MuteItem(Op.ADD, memberOpenId, toRfc3339(duration))));
    }

    /**
     * 更新单个群成员禁言到期时间（仅修改过期时间，不新增也不解除禁言）
     *
     * @param groupOpenId  群 OpenID
     * @param memberOpenId 被禁言成员的 OpenID
     * @param duration     新的禁言时长，从当前时间起算，不能为空
     * @return 是否成功
     */
    public static boolean updateMuteMember(String groupOpenId, String memberOpenId, Duration duration) {
        if (duration == null) {
            return false;
        }
        return setGroupMemberMute(groupOpenId, List.of(new MuteItem(Op.UPDATE, memberOpenId, toRfc3339(duration))));
    }

    /**
     * 解除单个群成员禁言
     *
     * @param groupOpenId  群 OpenID
     * @param memberOpenId 被解除禁言成员的 OpenID
     * @return 是否成功
     */
    public static boolean unmuteMember(String groupOpenId, String memberOpenId) {
        return setGroupMemberMute(groupOpenId, List.of(new MuteItem(Op.DEL, memberOpenId)));
    }

    /**
     * 批量设置群成员禁言（增/改/删），单次超过 10 个会自动分批调用
     *
     * @param groupOpenId 群 OpenID
     * @param members     禁言列表，每项通过 {@link MuteItem#op()} 控制 add/update/del
     * @return 是否全部成功；任一成员设置失败返回 false
     */
    public static boolean setGroupMemberMute(String groupOpenId, List<MuteItem> members) {
        if (groupOpenId == null || groupOpenId.isBlank() || members == null || members.isEmpty()) {
            return false;
        }
        boolean allSuccess = true;
        for (int i = 0; i < members.size(); i += MAX_MEMBERS_PER_CALL) {
            List<MuteItem> batch = members.subList(i, Math.min(i + MAX_MEMBERS_PER_CALL, members.size()));
            if (!doSetMute(groupOpenId, batch)) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /**
     * 查询群禁言状态，包含全员禁言模式与成员级禁言列表
     * 失败返回 null
     *
     * @param groupOpenId 群 OpenID
     * @return 群禁言状态，失败为 null
     */
    public static MuteStateResult queryMuteState(String groupOpenId) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            return null;
        }
        String url = getUrl(groupOpenId);
        String auth = authHeader(groupOpenId);
        String scene = "群禁言状态";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询群禁言状态失败，HTTP 状态码 {}，群ID为 {}", result.status(), groupOpenId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            if (response == null || response.isNull() || response.isMissingNode()) {
                log.error("[!] 查询群禁言状态失败，响应为空，群ID为 {}", groupOpenId);
                return null;
            }
            return new MuteStateResult(
                    parseGlobalRule(response.path("global_rule")),
                    parseMembers(response.path("members")));
        } catch (Exception e) {
            log.error("[!] 查询群禁言状态异常，群ID为 {}", groupOpenId, e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将禁言时长从当前时间起算，转为接口要求的 RFC3339 到期时间
     * 为空时返回 null，调用方据此省略 mute_expire_at 字段
     */
    private static String toRfc3339(Duration duration) {
        if (duration == null) {
            return null;
        }
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))
                .plus(duration)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static boolean doSetMute(String groupOpenId, List<MuteItem> members) {
        String url = getUrl(groupOpenId);
        String auth = authHeader(groupOpenId);
        String scene = "群禁言设置";
        String requestJson = null;
        try {
            List<Map<String, Object>> memberNodes = new ArrayList<>(members.size());
            for (MuteItem item : members) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("op", item.op().value());
                node.put("member_openid", item.memberOpenId());
                if (item.muteExpireAt() != null && !item.muteExpireAt().isBlank()) {
                    node.put("mute_expire_at", item.muteExpireAt());
                }
                memberNodes.add(node);
            }

            Map<String, Object> body = Map.of("members", memberNodes);
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.postJsonDetailed(url, requestJson, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "POST", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "POST", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 设置群成员禁言失败，HTTP 状态码 {}，群ID为 {}，成员列表为 {}", result.status(), groupOpenId, members);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "POST", url, requestJson,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 设置群成员禁言异常，群ID为 {}，成员列表为 {}", groupOpenId, members, e);
            OfficialSendLogRepository.recordError(null, scene, "POST", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    private static GlobalMuteRule parseGlobalRule(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        List<MuteScheduleRule> scheduleRules = new ArrayList<>();
        for (JsonNode rule : node.path("schedule_rules")) {
            scheduleRules.add(new MuteScheduleRule(
                    rule.path("task_id").asText(null),
                    rule.path("start_at").asText(null),
                    rule.path("end_at").asText(null),
                    rule.path("enabled").asBoolean(false)));
        }
        List<MuteRecurringRule> recurringRules = new ArrayList<>();
        for (JsonNode rule : node.path("recurring_rules")) {
            List<Integer> weekdays = new ArrayList<>();
            for (JsonNode day : rule.path("weekdays")) {
                weekdays.add(day.asInt());
            }
            recurringRules.add(new MuteRecurringRule(
                    rule.path("task_id").asText(null),
                    weekdays,
                    rule.path("start_time").asText(null),
                    rule.path("end_time").asText(null),
                    rule.path("enabled").asBoolean(false)));
        }
        return new GlobalMuteRule(node.path("mode").asText(null), scheduleRules, recurringRules);
    }

    private static List<MemberMuteState> parseMembers(JsonNode node) {
        List<MemberMuteState> members = new ArrayList<>();
        for (JsonNode m : node) {
            members.add(new MemberMuteState(
                    m.path("member_openid").asText(null),
                    m.path("mute_expire_at").asText(null),
                    m.path("username").asText(null),
                    m.path("union_openid").asText(null)));
        }
        return members;
    }

    /** 群禁言状态查询结果，globalRule 无全员禁言配置时可能为 null */
    public record MuteStateResult(GlobalMuteRule globalRule, List<MemberMuteState> members) {}

    /** 群级禁言规则（全员禁言配置） */
    public record GlobalMuteRule(String mode, List<MuteScheduleRule> scheduleRules, List<MuteRecurringRule> recurringRules) {}

    /** 定时禁言规则 */
    public record MuteScheduleRule(String taskId, String startAt, String endAt, boolean enabled) {}

    /** 周期禁言规则 */
    public record MuteRecurringRule(String taskId, List<Integer> weekdays, String startTime, String endTime, boolean enabled) {}

    /** 被禁言成员状态 */
    public record MemberMuteState(String memberOpenId, String muteExpireAt, String username, String unionOpenId) {}
}
