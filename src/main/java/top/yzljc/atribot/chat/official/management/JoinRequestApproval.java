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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName JoinRequestApproval
 * @Created_at 2026/08/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.management
 * @Description 入群申请审批
 * 机器人需拥有群管理员身份，接口频率限制 60 QPM
 */
@Slf4j
public final class JoinRequestApproval {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 审批动作：approve 通过，decline 拒绝 */
    public enum Op {
        APPROVE("approve"),
        DECLINE("decline");

        private final String value;

        Op(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** 列表分页：默认单页条数与最大条数 */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private static String getUrl(String groupOpenId, String memberOpenId) {
        return Config.getInstance().getQqApiBaseUrl()
                + "/v2/groups/" + groupOpenId + "/approval_join_request/" + memberOpenId;
    }

    /**
     * 通过入群申请
     *
     * @param groupOpenId   群 OpenID
     * @param memberOpenId  申请人 OpenID
     * @param joinRequestId 申请 ID，必填
     * @return 是否成功
     */
    public static boolean approveJoinRequest(String groupOpenId, String memberOpenId, String joinRequestId) {
        return doApproval(groupOpenId, memberOpenId, Op.APPROVE, joinRequestId, null, false);
    }

    /**
     * 拒绝入群申请
     *
     * @param groupOpenId   群 OpenID
     * @param memberOpenId  申请人 OpenID
     * @param joinRequestId 申请 ID，必填
     * @return 是否成功
     */
    public static boolean declineJoinRequest(String groupOpenId, String memberOpenId, String joinRequestId) {
        return doApproval(groupOpenId, memberOpenId, Op.DECLINE, joinRequestId, null, false);
    }

    /**
     * 拒绝入群申请，附带拒绝理由并选择是否加入群黑名单
     *
     * @param groupOpenId          群 OpenID
     * @param memberOpenId         申请人 OpenID
     * @param joinRequestId        申请 ID，必填
     * @param rejectReason         拒绝理由，可为空
     * @param addToMemberBlacklist 是否同时加入群黑名单
     * @return 是否成功
     */
    public static boolean declineJoinRequest(String groupOpenId, String memberOpenId, String joinRequestId,
                                             String rejectReason, boolean addToMemberBlacklist) {
        return doApproval(groupOpenId, memberOpenId, Op.DECLINE, joinRequestId, rejectReason, addToMemberBlacklist);
    }

    private static boolean doApproval(String groupOpenId, String memberOpenId, Op op,
                                      String joinRequestId, String rejectReason, boolean addToMemberBlacklist) {
        if (groupOpenId == null || groupOpenId.isBlank()
                || memberOpenId == null || memberOpenId.isBlank()
                || joinRequestId == null || joinRequestId.isBlank()) {
            return false;
        }
        String url = getUrl(groupOpenId, memberOpenId);
        String auth = "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
        String scene = "入群申请审批";
        String requestJson = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("op", op.value());
            body.put("join_request_id", joinRequestId);
            if (op == Op.DECLINE && rejectReason != null && !rejectReason.isBlank()) {
                body.put("reject_reason", rejectReason);
            }
            if (op == Op.DECLINE && addToMemberBlacklist) {
                body.put("add_to_member_blacklist", true);
            }
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.postJsonDetailed(url, requestJson, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "POST", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "POST", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 审批入群申请失败，HTTP 状态码 {}，群ID为 {}，申请人ID为 {}，操作为 {}",
                        result.status(), groupOpenId, memberOpenId, op);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "POST", url, requestJson,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 审批入群申请异常，群ID为 {}，申请人ID为 {}，操作为 {}", groupOpenId, memberOpenId, op, e);
            OfficialSendLogRepository.recordError(null, scene, "POST", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 拉取入群申请列表，next_cursor 为空串表示已到末页
     * 机器人需拥有群管理员身份，接口频率限制 30 QPM
     *
     * @param groupOpenId 群 OpenID
     * @param cursor      分页游标，首次查询可为空或空串
     * @param limit       单页数量，默认 20，最大 100
     * @return 申请列表结果，失败为 null
     */
    public static JoinRequestListResult getJoinRequestList(String groupOpenId, String cursor, int limit) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            return null;
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        String url = Config.getInstance().getQqApiBaseUrl()
                + "/v2/groups/" + groupOpenId + "/join_request_list?limit=" + limit;
        if (cursor != null && !cursor.isBlank()) {
            url += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        String auth = "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
        String scene = "入群申请列表";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", auth);
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询入群申请列表失败，HTTP 状态码 {}，群ID为 {}", result.status(), groupOpenId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            if (response == null || response.isNull() || response.isMissingNode()) {
                log.error("[!] 查询入群申请列表失败，响应为空，群ID为 {}", groupOpenId);
                return null;
            }
            List<JoinRequestData> list = new ArrayList<>();
            for (JsonNode node : response.path("list")) {
                list.add(parseJoinRequest(node));
            }
            return new JoinRequestListResult(list, response.path("next_cursor").asText(""));
        } catch (Exception e) {
            log.error("[!] 查询入群申请列表异常，群ID为 {}", groupOpenId, e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    private static JoinRequestData parseJoinRequest(JsonNode node) {
        JsonNode verify = node.path("verify_info");
        List<ReviewQa> qaList = new ArrayList<>();
        for (JsonNode qa : verify.path("review_qa_list")) {
            qaList.add(new ReviewQa(qa.path("question").asText(null), qa.path("answer").asText(null)));
        }
        VerifyInfoData verifyInfo = new VerifyInfoData(
                verify.path("method").asText(null),
                verify.path("verify_message").asText(null),
                qaList);
        return new JoinRequestData(
                node.path("join_request_id").asText(null),
                node.path("risk_tips").asText(""),
                node.path("union_openid").asText(null),
                node.path("member_openid").asText(null),
                node.path("username").asText(null),
                node.path("apply_at").asText(null),
                node.path("apply_source").asText(null),
                node.path("invited_by").asText(null),
                node.path("bot").asBoolean(false),
                verifyInfo);
    }

    /** 入群申请列表查询结果，nextCursor 为空串表示已到末页 */
    public record JoinRequestListResult(List<JoinRequestData> list, String nextCursor) {}

    /** 单条入群申请，applySource 取值 self_apply 主动申请 / invited 被邀请 */
    public record JoinRequestData(String joinRequestId, String riskTips, String unionOpenId, String memberOpenId,
                                  String username, String applyAt, String applySource, String invitedBy,
                                  boolean bot, VerifyInfoData verifyInfo) {}

    /** 用户入群验证方式，method 取值 verify_message / admin_review_qa */
    public record VerifyInfoData(String method, String verifyMessage, List<ReviewQa> reviewQaList) {}

    /** 管理员设置的问答 */
    public record ReviewQa(String question, String answer) {}
}
