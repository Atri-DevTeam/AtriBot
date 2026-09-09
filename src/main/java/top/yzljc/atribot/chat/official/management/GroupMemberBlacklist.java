package top.yzljc.atribot.chat.official.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupMemberBlacklist
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.management
 * @Description 群成员黑名单管理
 *
 * 查询接口频率限制 30 QPM，操作接口频率限制 60 QPM，单次最多操作 20 个成员
 */
@Slf4j
public final class GroupMemberBlacklist {

    private static final int MAX_MEMBERS_PER_CALL = 20;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 黑名单操作类型：add 加入黑名单，del 移出黑名单 */
    public enum Op {
        ADD("add"),
        DEL("del");

        private final String value;

        Op(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static String getUrl(String groupOpenId) {
        return Config.getInstance().getQqApiBaseUrl() + "/v2/groups/" + groupOpenId + "/member_blacklist";
    }

    /**
     * 查询群黑名单列表，next_cursor 为空串表示已到末页
     *
     * @param groupOpenId 群 OpenID
     * @param cursor      分页游标，首次查询可为空或空串
     * @param limit       单页数量，默认 20，最大 100
     * @return 黑名单列表结果，失败为 null
     */
    public static BlacklistListResult getMemberBlacklist(String groupOpenId, String cursor, int limit) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            return null;
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        String url = getUrl(groupOpenId) + "?limit=" + limit;
        if (cursor != null && !cursor.isBlank()) {
            url += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        String auth = "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
        String scene = "查询群黑名单";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询群黑名单失败，HTTP 状态码 {}，群ID为 {}", result.status(), groupOpenId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());
            return parseBlacklist(OBJECT_MAPPER.readTree(result.body()));
        } catch (Exception e) {
            log.error("[!] 查询群黑名单异常，群ID为 {}", groupOpenId, e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将单个成员加入群黑名单，目标成员必须已不在群内
     *
     * @param groupOpenId  群 OpenID
     * @param memberOpenId 成员 OpenID
     * @return 是否成功
     */
    public static boolean addMember(String groupOpenId, String memberOpenId) {
        if (memberOpenId == null || memberOpenId.isBlank()) {
            return false;
        }
        return setMemberBlacklist(groupOpenId, Op.ADD, List.of(memberOpenId));
    }

    /**
     * 将单个成员移出群黑名单
     *
     * @param groupOpenId  群 OpenID
     * @param memberOpenId 成员 OpenID
     * @return 是否成功
     */
    public static boolean removeMember(String groupOpenId, String memberOpenId) {
        if (memberOpenId == null || memberOpenId.isBlank()) {
            return false;
        }
        return setMemberBlacklist(groupOpenId, Op.DEL, List.of(memberOpenId));
    }

    /**
     * 批量操作群黑名单，单次超过 20 个会自动分批调用
     *
     * @param groupOpenId   群 OpenID
     * @param op            操作类型，add 加入黑名单，del 移出黑名单
     * @param memberOpenIds 成员 OpenID 列表，加入黑名单时目标成员必须已不在群内
     * @return 是否全部成功；任一成员操作失败返回 false，已成功的操作不回滚
     */
    public static boolean setMemberBlacklist(String groupOpenId, Op op, List<String> memberOpenIds) {
        if (groupOpenId == null || groupOpenId.isBlank() || op == null || !validMembers(memberOpenIds)) {
            return false;
        }
        boolean allSuccess = true;
        for (int i = 0; i < memberOpenIds.size(); i += MAX_MEMBERS_PER_CALL) {
            List<String> batch = memberOpenIds.subList(i, Math.min(i + MAX_MEMBERS_PER_CALL, memberOpenIds.size()));
            BlacklistOperationResult result = setMemberBlacklistDetailed(groupOpenId, op, batch);
            if (result == null || !result.failOpenIds().isEmpty()) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /**
     * 单批操作群黑名单，保留操作失败的成员列表
     *
     * @param groupOpenId   群 OpenID
     * @param op            操作类型，add 加入黑名单，del 移出黑名单
     * @param memberOpenIds 成员 OpenID 列表，单次最多 20 个
     * @return 操作结果，请求失败或参数无效为 null；failOpenIds 为空表示全部成功
     */
    public static BlacklistOperationResult setMemberBlacklistDetailed(String groupOpenId, Op op,
                                                                       List<String> memberOpenIds) {
        if (groupOpenId == null || groupOpenId.isBlank() || op == null || !validMembers(memberOpenIds)
                || memberOpenIds.size() > MAX_MEMBERS_PER_CALL) {
            return null;
        }
        String url = getUrl(groupOpenId);
        Map<String, Object> body = Map.of("op", op.value(), "member_openids", memberOpenIds);
        String auth = "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
        String scene = "操作群黑名单";
        String requestJson = null;
        try {
            requestJson = OBJECT_MAPPER.writeValueAsString(body);
            HttpService.PostResult result = HttpService.postJsonDetailed(url, requestJson, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "POST", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "POST", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 操作群黑名单失败，HTTP 状态码 {}，群ID为 {}", result.status(), groupOpenId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "POST", url, requestJson,
                    result.status(), result.body());
            return parseOperationResult(OBJECT_MAPPER.readTree(result.body()));
        } catch (Exception e) {
            log.error("[!] 操作群黑名单异常，群ID为 {}", groupOpenId, e);
            OfficialSendLogRepository.recordError(null, scene, "POST", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    private static boolean validMembers(List<String> memberOpenIds) {
        return memberOpenIds != null && !memberOpenIds.isEmpty()
                && memberOpenIds.stream().allMatch(id -> id != null && !id.isBlank());
    }

    static BlacklistListResult parseBlacklist(JsonNode response) {
        if (response == null || !response.path("users").isArray()) {
            throw new IllegalArgumentException("群黑名单响应缺少 users 数组");
        }
        List<BlacklistUser> users = new ArrayList<>();
        for (JsonNode user : response.path("users")) {
            users.add(new BlacklistUser(
                    user.path("union_openid").asText(null),
                    user.path("member_openid").asText(null),
                    user.path("username").asText(null),
                    user.path("banned_at").asText(null),
                    user.path("bot").asBoolean(false)));
        }
        return new BlacklistListResult(users, response.path("next_cursor").asText(""));
    }

    static BlacklistOperationResult parseOperationResult(JsonNode response) {
        if (response == null || !response.path("fail_openids").isArray()) {
            throw new IllegalArgumentException("群黑名单操作响应缺少 fail_openids 数组");
        }
        List<String> failOpenIds = new ArrayList<>();
        for (JsonNode id : response.path("fail_openids")) {
            failOpenIds.add(id.asText());
        }
        return new BlacklistOperationResult(failOpenIds);
    }

    /** 群黑名单查询结果，nextCursor 为空串表示已到末页 */
    public record BlacklistListResult(List<BlacklistUser> users, String nextCursor) {}

    /** 群黑名单成员，bannedAt 为 RFC3339 时间，unionOpenId 可能为空 */
    public record BlacklistUser(String unionOpenId, String memberOpenId, String username, String bannedAt, boolean bot) {}

    /** 群黑名单操作结果，failOpenIds 为空表示全部成功 */
    public record BlacklistOperationResult(List<String> failOpenIds) {}
}
