package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialBot
 * @Created_at 2026/07/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.official
 */
@Slf4j
public final class QQBot {

    /** 注意，这个B玩意是union_id不是user_openid，可能为null */
    public static String BOT_UNIONID;

    public static String BOT_AVATAR_URL;

    public static String BOT_NAME;

    public static String BOT_SHARE_LINK;

    public static String BOT_WELCOME_MSG;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 判定群已作废的 err_code（取自接口错误响应体中的 err_code 字段）：
     * 11255    - invalid request，群 openid 已失效
     * 40011026 - 机器人非群成员，群已解散或将机器人移出
     */
    private static final Set<String> DEFUNCT_GROUP_ERR_CODES = Set.of("11255", "40011026");

    private static final String BOT_GROUP_STATE_URL = Config.getInstance().getQqApiBaseUrl() + "/v2/groups/{group_openid}/bot_state";

    private static final String GROUP_INFO_URL = Config.getInstance().getQqApiBaseUrl() + "/v2/groups/{group_openid}/info";

    private static final EndpointRateLimiter BOT_GROUP_STATE_LIMITER = new EndpointRateLimiter("群聊 /bot_state", 60);

    private static final EndpointRateLimiter GROUP_INFO_LIMITER = new EndpointRateLimiter("群聊 /info", 60);

    public static void fetchBotInfo() {
        var url = Config.getInstance().getQqApiBaseUrl() + "/users/@me";
        var d = HttpService.sendGetRequest(url, "Authorization", "QQBot " + Atri.getInstance().getTokenManager().getAccessToken());
        if (d == null) {
            d = HttpService.sendGetRequest(url);
            log.warn("获取机器人信息失败，尝试重新获取一次");
        }
        if (d == null) {
            log.error("获取机器人信息失败，请检查网络连接或API服务是否正常");
            Alert.notify("获取机器人信息失败，请检查网络连接或API服务是否正常");
            return;
        }

        BOT_UNIONID = d.path("union_openid").asText(null);
        BOT_AVATAR_URL = d.path("avatar").asText(null);
        BOT_NAME = d.path("username").asText(null);
        BOT_SHARE_LINK = d.path("share_url").asText(null);
        BOT_WELCOME_MSG = d.path("welcome_msg").asText(null);

        log.info("Fetched bot info: unionid={}, avatar_url={}, name={}, share_link={}, welcome_msg={}",
                BOT_UNIONID, BOT_AVATAR_URL, BOT_NAME, BOT_SHARE_LINK, BOT_WELCOME_MSG);
    }

    /**
     * 获取群资料，失败返回 null（供加群、WebUI 同步等不需要区分错误原因的场景使用）
     */
    public static GroupProfile fetchGroupProfile(String groupId) {
        return fetchGroupProfileDetailed(groupId).profile();
    }

    /**
     * 获取群资料，同时携带接口错误信息，用于判断群是否已作废
     */
    public static GroupProfileResult fetchGroupProfileDetailed(String groupId) {
        return fetchGroupProfileDetailed(groupId, false);
    }

    /**
     * 获取群资料，同时携带接口错误信息，用于判断群是否已作废
     *
     * @param logOnlyOnError 定时批量刷新时传 true，正常响应的调用不写入发送日志，仅在出错时记录 ERROR
     */
    public static GroupProfileResult fetchGroupProfileDetailed(String groupId, boolean logOnlyOnError) {
        if (groupId == null || groupId.isBlank()) {
            log.warn("获取群资料失败，groupId 为空");
            return new GroupProfileResult(null, null);
        }

        String token = Atri.getInstance().getTokenManager().getAccessToken();
        String authHeader = "QQBot " + token;
        String groupInfoUrl = GROUP_INFO_URL.replace("{group_openid}", groupId);
        String botGroupStateUrl = BOT_GROUP_STATE_URL.replace("{group_openid}", groupId);

        if (!BOT_GROUP_STATE_LIMITER.waitForRateLimit()) {
            return new GroupProfileResult(null, null);
        }
        HttpService.GetResult botStateResult = HttpService.sendGetRequestDetailed(botGroupStateUrl, "Authorization", authHeader);
        logGroupProfileCall("群机器人状态", botGroupStateUrl, botStateResult, logOnlyOnError);

        if (!GROUP_INFO_LIMITER.waitForRateLimit()) {
            return new GroupProfileResult(null, null);
        }
        HttpService.GetResult groupInfoResult = HttpService.sendGetRequestDetailed(groupInfoUrl, "Authorization", authHeader);
        logGroupProfileCall("群资料", groupInfoUrl, groupInfoResult, logOnlyOnError);

        String botStateErrCode = extractErrCode(botStateResult);
        String groupInfoErrCode = extractErrCode(groupInfoResult);

        if (botStateErrCode == null && groupInfoErrCode == null) {
            JsonNode botState = parseBody(botStateResult);
            JsonNode groupInfo = parseBody(groupInfoResult);
            if (botState != null && groupInfo != null) {
                GroupProfile profile = new GroupProfile(
                        botState.path("member_openid").asText(null),
                        botState.path("joined_at").asText(null),
                        botState.path("allow_proactive_msg").asBoolean(false),
                        GroupProfile.Scope.from(botState.path("recv_msg_setting").asText(null)),
                        PlatformRole.getPlatformRole(botState.path("member_role").asText(null)),
                        groupInfo.path("group_openid").asText(groupId),
                        groupInfo.path("group_name").asText(null),
                        groupInfo.path("group_finger_memo").asText(null),
                        groupInfo.path("group_class_text").asText(null),
                        parseGroupTags(groupInfo.path("group_tags")),
                        groupInfo.path("group_member_num").asInt(0)
                );
                return new GroupProfileResult(profile, null);
            }
        }

        String errCode = botStateErrCode != null ? botStateErrCode : groupInfoErrCode;
        log.warn("获取群资料失败，groupId={}, bot_state(status={}, err_code={}), group_info(status={}, err_code={})",
                groupId, botStateResult.status(), botStateErrCode, groupInfoResult.status(), groupInfoErrCode);
        return new GroupProfileResult(null, errCode);
    }

    /**
     * 从接口错误响应体中提取 err_code
     */
    private static String extractErrCode(HttpService.GetResult result) {
        if (result == null || (result.status() >= 200 && result.status() < 300)) {
            return null;
        }
        String body = result.body();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode errNode = node.get("err_code");
            return (errNode != null && !errNode.isNull()) ? errNode.asText() : null;
        } catch (Exception e) {
            log.warn("解析接口错误响应 err_code 失败: {}", e.getMessage());
            return null;
        }
    }

    private static JsonNode parseBody(HttpService.GetResult result) {
        String body = result == null ? null : result.body();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("解析群资料响应体失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 记录一次群资料 HTTP 调用日志
     *
     * @param scene          接口场景，如"群机器人状态"、"群资料"
     * @param logOnlyOnError true 时只在出错时记录 ERROR（定时批量轮询用），否则正常记录 SEND/RESPONSE/ERROR
     */
    private static void logGroupProfileCall(String scene, String url, HttpService.GetResult result, boolean logOnlyOnError) {
        boolean isError = result.status() < 200 || result.status() >= 300;
        if (logOnlyOnError) {
            if (isError) {
                OfficialSendLogRepository.recordError(null, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
            }
            return;
        }

        String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
        if (isError) {
            OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                    result.status(), result.body(), "HTTP 状态异常: " + result.status());
        } else {
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());
        }
    }

    private static List<String> parseGroupTags(JsonNode tagsNode) {
        List<String> tags = new ArrayList<>();
        if (tagsNode == null || !tagsNode.isArray()) {
            return tags;
        }
        for (JsonNode tagNode : tagsNode) {
            if (tagNode == null || tagNode.isNull()) {
                continue;
            }
            tags.add(tagNode.asText());
        }
        return tags;
    }

    /**
     * 群资料获取结果
     *
     * @param profile 成功时为群资料，失败为 null
     * @param errCode 失败时接口返回的 err_code，成功为 null
     */
    public record GroupProfileResult(GroupProfile profile, String errCode) {

        public boolean isDefunctGroup() {
            return errCode != null && DEFUNCT_GROUP_ERR_CODES.contains(errCode);
        }
    }

    private static final class EndpointRateLimiter {
        private static final long WINDOW_MS = 60_000;

        private final String endpointName;
        private final int qpmLimit;
        private final Deque<Long> timestamps = new ArrayDeque<>();

        private EndpointRateLimiter(String endpointName, int qpmLimit) {
            this.endpointName = endpointName;
            this.qpmLimit = qpmLimit;
        }

        private synchronized boolean waitForRateLimit() {
            while (true) {
                long now = System.currentTimeMillis();
                pruneExpired(now - WINDOW_MS);
                if (timestamps.size() < qpmLimit) {
                    timestamps.offerLast(now);
                    return true;
                }

                Long oldest = timestamps.peekFirst();
                if (oldest != null) {
                    long waitMs = oldest + WINDOW_MS - now + 50;
                    if (waitMs > 0) {
                        log.info("{} 已达 {} QPM 限制，等待 {}ms", endpointName, qpmLimit, waitMs);
                        try {
                            wait(waitMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("{} 频控等待被中断", endpointName);
                            return false;
                        }
                    }
                }
            }
        }

        private void pruneExpired(long cutoff) {
            while (true) {
                Long oldest = timestamps.peekFirst();
                if (oldest == null || oldest >= cutoff) break;
                timestamps.pollFirst();
            }
        }
    }
}
