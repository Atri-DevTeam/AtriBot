package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.OfficialGroupSendFailEvent;
import top.yzljc.atribot.event.events.OfficialC2CSendFailEvent;
import top.yzljc.atribot.event.impl.ErrorCode;
import top.yzljc.atribot.function.official.ChatContentRecord;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.platform.qq.TokenManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author YZ_Ljc_
 * @ClassName ChatService
 * @Created_at 2026/05/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 * @Description
 * 聊天服务底层管道
 */
@Slf4j
@Getter
public class ChatService {

    private static final String EMERGENCY_PAUSED_MESSAGE = "机器人因暂时重启维护或出现问题已被开发者暂停响应，请稍后重试！";
    @Getter
    @Setter
    private static volatile boolean emergencyPaused = false;

    private final String apiBaseUrl;
    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageBodyFactory bodyFactory;
    private final OfficialMediaUploader mediaUploader;
    private final ActiveMessageRateLimiter activeRateLimiter;
    private final PrivateStreamMessage privateStreamHelper;

    private final Cache<String, AtomicInteger> msgSeqCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public ChatService(String apiBaseUrl, TokenManager tokenManager) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
        this.bodyFactory = new MessageBodyFactory(this::getNextMsgSeq);
        this.mediaUploader = new OfficialMediaUploader(tokenManager, objectMapper, bodyFactory);
        this.activeRateLimiter = new ActiveMessageRateLimiter();
        this.privateStreamHelper = new PrivateStreamMessage(apiBaseUrl, tokenManager, objectMapper, bodyFactory, this::getNextMsgSeq);
    }

    static String emergencyPausedMessage() {
        return EMERGENCY_PAUSED_MESSAGE;
    }

    /** 获取单聊消息 API URL */
    public String privateMessageUrl(String openId) {
        return apiBaseUrl + "/v2/users/" + openId + "/messages";
    }

    /** 获取单聊文件上传 API URL */
    public String privateFileUrl(String openId) {
        return apiBaseUrl + "/v2/users/" + openId + "/files";
    }

    /** 获取群聊消息 API URL */
    public String groupMessageUrl(String groupOpenId) {
        return apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages";
    }

    /** 获取群聊文件上传 API URL */
    public String groupFileUrl(String groupOpenId) {
        return apiBaseUrl + "/v2/groups/" + groupOpenId + "/files";
    }

    /** 频道文字子频道消息 API URL */
    public String guildChannelMessageUrl(String channelId) {
        return apiBaseUrl + "/channels/" + channelId + "/messages";
    }

    /** 频道私聊消息 API URL */
    public String guildDirectMessageUrl(String guildId) {
        return apiBaseUrl + "/dms/" + guildId + "/messages";
    }

    /**
     * 异步发送单聊消息，成功后自动记录到 ChatContentRecord
     *
     * @param openId  用户 openId
     * @param request 消息体
     * @return 消息 ID 的 Future
     */
    public CompletableFuture<String> sendPrivateMessageAsync(String openId, MessageBody request) {
        MessageBody effectiveRequest = emergencyPauseRequestOrNull(request, "单聊");
        if (effectiveRequest == null) {
            return CompletableFuture.completedFuture(null);
        }
        return sendMessageAsync(privateMessageUrl(openId), effectiveRequest, "单聊")
                .thenApply(response -> {
                    if (response != null) {
                        ChatContentRecord.recordSentC2CMessage(openId, effectiveRequest, response.id(), response.refIdx(), response.timestamp());
                    }
                    if (response != null) {
                        return response.id() ;
                    } else {
                        return null;
                    }
                });
    }

    /**
     * 异步发送群聊消息，成功后记录到 ChatContentRecord，并处理主动消息频控与白名单
     *
     * @param groupOpenId 群 openId
     * @param request     消息体
     * @return 消息 ID 的 Future
     */
    public CompletableFuture<String> sendGroupMessageAsync(String groupOpenId, MessageBody request) {
        MessageBody effectiveRequest = emergencyPauseRequestOrNull(request, "群聊");
        if (effectiveRequest == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (effectiveRequest.getMsgId() == null && effectiveRequest.getEventId() == null) {
            activeRateLimiter.checkPerGroupActiveRate(groupOpenId);
        }
        return sendMessageAsync(groupMessageUrl(groupOpenId), effectiveRequest, "群聊")
                .thenApply(response -> {
                    if (response != null) {
                        ChatContentRecord.recordSentGroupMessage(groupOpenId, effectiveRequest, response.id(), response.refIdx(), response.timestamp());
                        if (effectiveRequest.getMsgId() == null && !OfficialGroups.allowProactiveMsg(groupOpenId)) {
                            OfficialGroups.setAllowProactiveMsg(groupOpenId, true);
                        }
                    }
                    if (response != null) {
                        return response.id() ;
                    } else {
                        return null;
                    }
                });
    }

    /**
     * 异步发送文字子频道消息
     *
     * @param channelId   频道 ID
     * @param request     消息体
     * @return 消息 ID 的 Future
     */
    public CompletableFuture<String> sendGuildChannelMessageAsync(String channelId, MessageBody request) {
        MessageBody pendingRequest = emergencyPauseRequestOrNull(request, "文字子频道");
        if (pendingRequest == null) {
            return CompletableFuture.completedFuture(null);
        }
        return sendMessageAsync(guildChannelMessageUrl(channelId), pendingRequest, "文字子频道")
                .thenApply(response -> {
                    if (response != null) {
                        return response.id();
                    } else {
                        return null;
                    }
                });
    }

    /**
     * 异步发送频道私信消息
     *
     * @param guildId   频道 ID
     * @param request     消息体
     * @return 消息 ID 的 Future
     */
    public CompletableFuture<String> sendGuildDirectMessageAsync(String guildId, MessageBody request) {
        MessageBody pendingRequest = emergencyPauseRequestOrNull(request, "频道私信");
        if (pendingRequest == null) {
            return CompletableFuture.completedFuture(null);
        }
        return sendMessageAsync(guildDirectMessageUrl(guildId), pendingRequest, "频道私信")
                .thenApply(response -> {
                    if (response != null) {
                        return response.id();
                    } else {
                        return null;
                    }
                });
    }

    /**
     * 撤回单聊消息
     *
     * @param userOpenId 用户 openId
     * @param messageId  消息 ID
     */
    public boolean recallPrivateMessage(String userOpenId, String messageId) {
        String url = apiBaseUrl + "/v2/users/" + userOpenId + "/messages/" + messageId;
        try {
            var t = HttpService.deleteRequestStr(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
            return !t.equals("error");
        } catch (Exception e) {
            log.error("撤回单聊消息失败, unionOpenId: {}, messageId: {}", userOpenId, messageId, e);
            return false;
        }
    }

    private MessageBody emergencyPauseRequestOrNull(MessageBody request, String logType) {
        if (!emergencyPaused) {
            return request;
        }
        if (request == null || isActiveRequest(request)) {
            log.warn("{}主动消息已被应急暂停拦截", logType);
            return null;
        }
        if (request.getMsgId() != null && !request.getMsgId().isBlank()) {
            return bodyFactory.replyText(request.getMsgId(), EMERGENCY_PAUSED_MESSAGE);
        }
        return bodyFactory.eventText(request.getEventId(), EMERGENCY_PAUSED_MESSAGE);
    }

    private static boolean isActiveRequest(MessageBody request) {
        return isBlank(request.getMsgId()) && isBlank(request.getEventId());
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 撤回群聊消息
     *
     * @param groupOpenId 群 openId
     * @param messageId   消息 ID
     */
    public boolean recallGroupMessage(String groupOpenId, String messageId) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages/" + messageId;
        try {
            var t = HttpService.deleteRequestStr(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
            return !t.equals("error");
        } catch (Exception e) {
            log.error("撤回群聊消息失败, groupOpenId: {}, messageId: {}", groupOpenId, messageId, e);
            return false;
        }
    }

    /**
     * 获取群成员信息（暂不可用）
     *
     * @param userOpenId  用户 openId
     * @param groupOpenId 群 openId
     * @return 用户信息 JSON 字符串，失败返回 null
     */
    public String getUserInfo(String userOpenId, String groupOpenId) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/members/" + userOpenId;
        try {
            JsonNode response = HttpService.sendGetRequest(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
            if (response == null || response.isEmpty()) {
                log.error("获取用户信息失败，服务器返回为空, userOpenId: {}, groupOpenId: {}", userOpenId, groupOpenId);
                return null;
            }
            return response.toString();
        } catch (Exception e) {
            log.error("获取用户信息失败, userOpenId: {}, groupOpenId: {}", userOpenId, groupOpenId, e);
            return null;
        }
    }

    private int getNextMsgSeq(String msgId) {
        if (msgId == null) return 1;
        try {
            return msgSeqCache.get(msgId, () -> new AtomicInteger(0)).incrementAndGet();
        } catch (Exception e) {
            log.error("获取 msg_seq 异常, msgId: {}", msgId, e);
            return 1;
        }
    }

    private CompletableFuture<ChatResponse> sendMessageAsync(String url, MessageBody request, String logType) {
        return ThreadManager.supplyAsync(() -> doSendMessage(url, request, logType));
    }

    private ChatResponse doSendMessage(String url, MessageBody request, String logType) {
        if (request.getMsgId() == null && request.getEventId() == null && logType.equals("群聊")) {
            activeRateLimiter.waitForActiveRateLimit();
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            OfficialSendLogRepository.recordError(null, logType, "POST", url, null,
                    null, null, "消息序列化失败: " + e.getMessage());
            log.error("{}消息序列化失败: ", logType, e);
            return null;
        }

        String traceId = OfficialSendLogRepository.recordSend(logType, "POST", url, json);
        var res = HttpService.postJsonDetailed(url, json,
                "Authorization", "QQBot " + tokenManager.getAccessToken());
        try {
            if (res.status() >= 200 && res.status() < 300 && res.body() != null && !res.body().isBlank()) {
                JsonNode result = objectMapper.readTree(res.body());
                JsonNode idNode = result.get("id");
                String timestamp = result.path("timestamp").asText(null);
                String refIdx = result.path("ext_info").path("ref_idx").asText(null);
                String id;

                if (idNode != null && !idNode.asText().isBlank()) {
                    id = idNode.asText();
                    OfficialSendLogRepository.recordResponse(traceId, logType, "POST", url, json,
                            res.status(), res.body());
                    return new ChatResponse(id, timestamp, refIdx);
                }
                OfficialSendLogRepository.recordError(traceId, logType, "POST", url, json,
                        res.status(), res.body(), "返回无 id");
                log.error("{}消息发送失败, 返回无 id: {}", logType, result);
                throw QQMessageSendException.fromResponse(objectMapper, res.body(), "官方接口未返回消息ID");
            } else {
                OfficialSendLogRepository.recordError(traceId, logType, "POST", url, json,
                        res.status(), res.body(), "HTTP 状态异常或响应为空");
                log.error("{}消息发送失败, status: {}, body: {}", logType, res.status(), res.body());
                if (res.body() != null) {
                    try {
                        JsonNode err = objectMapper.readTree(res.body());
                        int code = err.path("err_code").asInt(0);
                        String msg = err.path("message").asText(null);
                        // 仅在主动消息无权限(40034105)时才触发事件，其余失败不报
                        if (code == ErrorCode.NO_ACTIVE_MESSAGE_PERMISSION.getErrorCode()) {
                            if (logType.equals("群聊") && url.contains("/groups/")) {
                                String gid = url.substring(url.indexOf("/groups/") + 8, url.indexOf("/messages"));
                                EventManager.getInstance().callEvent(new OfficialGroupSendFailEvent(gid, code, msg));
                            }
                            if (logType.equals("单聊") && url.contains("users")) {
                                String userId = url.substring(url.indexOf("/users/") + 7, url.indexOf("/messages"));
                                EventManager.getInstance().callEvent(new OfficialC2CSendFailEvent(userId, code, msg));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                throw QQMessageSendException.fromResponse(objectMapper, res.body(), "消息发送失败");
            }
        } catch (JsonProcessingException e) {
            OfficialSendLogRepository.recordError(traceId, logType, "POST", url, json,
                    res.status(), res.body(), "响应解析失败: " + e.getMessage());
            log.error("{}消息响应解析失败: ", logType, e);
            return null;
        }
    }

    private record ChatResponse(String id, String timestamp, String refIdx) {}
}
