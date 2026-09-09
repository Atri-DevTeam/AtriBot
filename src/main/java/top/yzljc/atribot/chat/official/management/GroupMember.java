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
 * @ClassName GroupMember
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.management
 * @Description 群成员查询与移除管理
 *
 * 成员列表每页最多 30 条，接口频率限制 60 QPM；
 * 成员信息查询与批量移除接口频率限制 30 QPM，单次最多移除 20 个成员
 */
@Slf4j
public final class GroupMember {

    private static final int MAX_MEMBERS_PER_CALL = 20;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static String getUrl(String groupOpenId) {
        return Config.getInstance().getQqApiBaseUrl() + "/v2/groups/" + groupOpenId;
    }

    /**
     * 查询群成员列表，next_cursor 为空串表示已到末页
     *
     * @param groupOpenId 群 OpenID
     * @param cursor      分页游标，首次查询可为空或空串
     * @return 成员列表结果，失败为 null
     */
    public static MemberListResult getMemberList(String groupOpenId, String cursor) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            return null;
        }
        String url = getUrl(groupOpenId) + "/members";
        if (cursor != null && !cursor.isBlank()) {
            url += "?cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        String auth = "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
        String scene = "查询群成员列表";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询群成员列表失败，HTTP 状态码 {}，群ID为 {}", result.status(), groupOpenId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());
            return parseMemberList(OBJECT_MAPPER.readTree(result.body()));
        } catch (Exception e) {
            log.error("[!] 查询群成员列表异常，群ID为 {}", groupOpenId, e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 查询指定群成员信息
     *
     * @param groupOpenId  群 OpenID
     * @param memberOpenId 成员 OpenID
     * @return 成员信息，失败为 null
     */
    public static MemberInfo getMemberInfo(String groupOpenId, String memberOpenId) {
        if (groupOpenId == null || groupOpenId.isBlank()
                || memberOpenId == null || memberOpenId.isBlank()) {
            return null;
        }
        String url = getUrl(groupOpenId) + "/members/" + memberOpenId;
        String auth = "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
        String scene = "查询群成员信息";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询群成员信息失败，HTTP 状态码 {}，群ID为 {}", result.status(), groupOpenId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());
            return parseMember(OBJECT_MAPPER.readTree(result.body()));
        } catch (Exception e) {
            log.error("[!] 查询群成员信息异常，群ID为 {}", groupOpenId, e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 移除单个群成员，可选择同时加入群黑名单
     *
     * @param groupOpenId          群 OpenID
     * @param memberOpenId         成员 OpenID
     * @param addToMemberBlacklist 是否同时加入群黑名单
     * @return 是否全部成功，移除或拉黑失败均返回 false
     */
    public static boolean removeMember(String groupOpenId, String memberOpenId, boolean addToMemberBlacklist) {
        if (memberOpenId == null || memberOpenId.isBlank()) {
            return false;
        }
        return removeMembers(groupOpenId, List.of(memberOpenId), addToMemberBlacklist);
    }

    /**
     * 批量移除群成员，单次超过 20 个会自动分批调用
     *
     * @param groupOpenId          群 OpenID
     * @param memberOpenIds        成员 OpenID 列表
     * @param addToMemberBlacklist 是否同时加入群黑名单
     * @return 是否全部成功；任一批次移除或拉黑失败返回 false，已成功的操作不回滚
     */
    public static boolean removeMembers(String groupOpenId, List<String> memberOpenIds, boolean addToMemberBlacklist) {
        if (groupOpenId == null || groupOpenId.isBlank() || !validMembers(memberOpenIds)) {
            return false;
        }
        boolean allSuccess = true;
        for (int i = 0; i < memberOpenIds.size(); i += MAX_MEMBERS_PER_CALL) {
            List<String> batch = memberOpenIds.subList(i, Math.min(i + MAX_MEMBERS_PER_CALL, memberOpenIds.size()));
            RemoveMembersResult result = removeMembersDetailed(groupOpenId, batch, addToMemberBlacklist);
            if (result == null || !result.success()) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /**
     * 单批移除群成员，保留移除结果及拉黑失败的成员列表
     *
     * @param groupOpenId          群 OpenID
     * @param memberOpenIds        成员 OpenID 列表，单次最多 20 个
     * @param addToMemberBlacklist 是否同时加入群黑名单
     * @return 移除结果，请求失败或参数无效为 null；部分失败需检查结果字段
     */
    public static RemoveMembersResult removeMembersDetailed(String groupOpenId, List<String> memberOpenIds,
                                                             boolean addToMemberBlacklist) {
        if (groupOpenId == null || groupOpenId.isBlank() || !validMembers(memberOpenIds)
                || memberOpenIds.size() > MAX_MEMBERS_PER_CALL) {
            return null;
        }
        String url = getUrl(groupOpenId) + "/batch_remove_members";
        Map<String, Object> body = Map.of(
                "member_openids", memberOpenIds,
                "add_to_member_blacklist", addToMemberBlacklist);
        String auth = "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
        String scene = "移除群成员";
        String requestJson = null;
        try {
            requestJson = OBJECT_MAPPER.writeValueAsString(body);
            HttpService.PostResult result = HttpService.postJsonDetailed(url, requestJson, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "POST", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "POST", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 移除群成员失败，HTTP 状态码 {}，群ID为 {}", result.status(), groupOpenId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "POST", url, requestJson,
                    result.status(), result.body());
            return parseRemoveResult(OBJECT_MAPPER.readTree(result.body()));
        } catch (Exception e) {
            log.error("[!] 移除群成员异常，群ID为 {}", groupOpenId, e);
            OfficialSendLogRepository.recordError(null, scene, "POST", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    private static boolean validMembers(List<String> memberOpenIds) {
        return memberOpenIds != null && !memberOpenIds.isEmpty()
                && memberOpenIds.stream().allMatch(id -> id != null && !id.isBlank());
    }

    static MemberListResult parseMemberList(JsonNode response) {
        if (response == null || !response.path("members").isArray()) {
            throw new IllegalArgumentException("群成员列表响应缺少 members 数组");
        }
        List<MemberInfo> members = new ArrayList<>();
        for (JsonNode member : response.path("members")) {
            members.add(parseMember(member));
        }
        return new MemberListResult(members, response.path("next_cursor").asText(""));
    }

    static MemberInfo parseMember(JsonNode node) {
        if (node == null || node.path("member_openid").asText("").isBlank()) {
            throw new IllegalArgumentException("群成员信息响应缺少 member_openid");
        }
        return new MemberInfo(
                node.path("member_openid").asText(null),
                node.path("username").asText(null),
                node.path("member_role").asText(null),
                node.path("bot").asBoolean(false),
                node.path("joined_at").asText(null),
                node.path("union_openid").asText(null));
    }

    static RemoveMembersResult parseRemoveResult(JsonNode response) {
        if (response == null || !response.path("remove_members_result").isTextual()
                || !response.path("add_to_member_blacklist_fail_openids").isArray()) {
            throw new IllegalArgumentException("群成员移除响应缺少结果或失败成员列表");
        }
        List<String> failOpenIds = new ArrayList<>();
        for (JsonNode id : response.path("add_to_member_blacklist_fail_openids")) {
            failOpenIds.add(id.asText());
        }
        return new RemoveMembersResult(response.path("remove_members_result").asText(), failOpenIds);
    }

    /** 群成员列表查询结果，nextCursor 为空串表示已到末页 */
    public record MemberListResult(List<MemberInfo> members, String nextCursor) {}

    /** 群成员信息，memberRole 为 member/owner/admin，joinedAt 为 RFC3339 时间，unionOpenId 可能为空 */
    public record MemberInfo(String memberOpenId, String username, String memberRole,
                             boolean bot, String joinedAt, String unionOpenId) {}

    /** 群成员移除结果，移除成功但拉黑失败时仍会保留失败的 OpenID */
    public record RemoveMembersResult(String removeMembersResult, List<String> addToMemberBlacklistFailOpenIds) {
        /** 是否移除成功且没有拉黑失败的成员 */
        public boolean success() {
            return "success".equals(removeMembersResult) && addToMemberBlacklistFailOpenIds.isEmpty();
        }
    }
}
