package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.OfficialGroupSendFailEvent;
import top.yzljc.atribot.event.events.OfficialC2CSendFailEvent;
import top.yzljc.atribot.event.impl.ErrorCode;
import top.yzljc.atribot.function.tasks.QQChatContentRecord;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.platform.qq.TokenManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.test.WhatFuckingPing;

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

    private static final String EMERGENCY_PAUSED_MESSAGE = "开发者暂且维护中，马上回来！";
    private static final ImageComponent MAINTENANCE = ImageComponent.imageOf(ResourcesProperties.MAINTENANCE_IMG).setText(EMERGENCY_PAUSED_MESSAGE);
    @Getter
    @Setter
    private static volatile boolean emergencyPaused = false;

    private final String apiBaseUrl;
    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageBodyFactory bodyFactory;
    private final OfficialMediaUploader mediaUploader;
    private final ActiveMessageRateLimiter activeRateLimiter;
    private final C2CStreamMessage privateStreamHelper;

    private final Cache<String, AtomicInteger> msgSeqCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public ChatService(String apiBaseUrl, TokenManager tokenManager) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
        this.bodyFactory = new MessageBodyFactory(this::getNextMsgSeq);
        this.mediaUploader = new OfficialMediaUploader(tokenManager, objectMapper, bodyFactory);
        this.activeRateLimiter = new ActiveMessageRateLimiter();
        this.privateStreamHelper = new C2CStreamMessage(apiBaseUrl, tokenManager, objectMapper, bodyFactory,
                this::getNextMsgSeq, this::sendPrivateMaintenanceMessageAsync);
    }

    /**
     * 获取暂停状态的维护图片组件
     *
     * @return 维护图片及其附带文字
     */
    static ImageComponent emergencyPausedMessage() {
        return MAINTENANCE;
    }

    /**
     * 获取单聊消息 API URL
     *
     * @param openId 用户 openId
     * @return 单聊消息 API URL
     */
    public String privateMessageUrl(String openId) {
        return apiBaseUrl + "/v2/users/" + openId + "/messages";
    }

    /**
     * 获取单聊文件上传 API URL
     *
     * @param openId 用户 openId
     * @return 单聊文件上传 API URL
     */
    public String privateFileUrl(String openId) {
        return apiBaseUrl + "/v2/users/" + openId + "/files";
    }

    /**
     * 获取群聊消息 API URL
     *
     * @param groupOpenId 群 openId
     * @return 群聊消息 API URL
     */
    public String groupMessageUrl(String groupOpenId) {
        return apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages";
    }

    /**
     * 获取群聊文件上传 API URL
     *
     * @param groupOpenId 群 openId
     * @return 群聊文件上传 API URL
     */
    public String groupFileUrl(String groupOpenId) {
        return apiBaseUrl + "/v2/groups/" + groupOpenId + "/files";
    }

    /**
     * 频道文字子频道消息 API URL
     *
     * @param channelId 子频道 ID
     * @return 频道文字子频道消息 API URL
     */
    public String guildChannelMessageUrl(String channelId) {
        return apiBaseUrl + "/channels/" + channelId + "/messages";
    }

    /**
     * 频道私聊消息 API URL
     *
     * @param guildId 频道 ID
     * @return 频道私聊消息 API URL
     */
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
        return prepareMessageAsync(request, privateFileUrl(openId), "单聊").thenCompose(effectiveRequest -> {
            if (effectiveRequest == null) {
                return CompletableFuture.completedFuture(null);
            }
            return sendMessageAsync(privateMessageUrl(openId), effectiveRequest, "单聊")
                    .thenApply(response -> {
                        if (response != null) {
                            QQChatContentRecord.recordSentC2CMessage(openId, effectiveRequest, response.id(), response.refIdx(), response.timestamp());
                        }
                        if (response != null) {
                            return response.id() ;
                        } else {
                            return null;
                        }
                    });
        });
    }

    /**
     * 异步发送单聊召回消息，设置 is_wakeup 并清除被动回复来源及引用
     *
     * @param openId  用户 openId
     * @param request 消息体，可为 null；不会修改原始消息体
     * @return 消息 ID 的 Future，消息为空、暂停发送或发送失败返回 null
     */
    public CompletableFuture<String> sendPrivateWakeupMessageAsync(String openId, MessageBody request) {
        return sendPrivateMessageAsync(openId, bodyFactory.wakeup(request));
    }

    /**
     * 异步发送单聊正在输入通知，单独处理空 JSON 对象响应，不记录为聊天消息
     *
     * @param openId      用户 openId
     * @param inputSecond 输入状态持续秒数，必须大于 0
     * @return HTTP 成功且返回空 JSON 对象时为 true；参数无效、暂停或发送失败时为 false
     */
    public CompletableFuture<Boolean> sendPrivateInputNotifyAsync(String openId, int inputSecond) {
        if (openId == null || openId.isBlank() || inputSecond <= 0 || emergencyPaused) {
            return CompletableFuture.completedFuture(false);
        }
        return ThreadManager.supplyAsync(() -> {
            if (emergencyPaused) return false;
            String url = privateMessageUrl(openId);
            String logType = "单聊输入状态";
            String json = null;
            String traceId = null;
            Integer status = null;
            String responseBody = null;
            try {
                json = objectMapper.writeValueAsString(bodyFactory.inputNotify(inputSecond));
                traceId = OfficialSendLogRepository.recordSend(logType, "POST", url, json);
                var res = HttpService.postJsonDetailed(url, json,
                        "Authorization", "QQBot " + tokenManager.getAccessToken());
                status = res.status();
                responseBody = res.body();
                if (status >= 200 && status < 300 && responseBody != null && !responseBody.isBlank()) {
                    JsonNode result = objectMapper.readTree(responseBody);
                    if (result != null && result.isObject() && result.isEmpty()) {
                        OfficialSendLogRepository.recordResponse(traceId, logType, "POST", url, json,
                                status, responseBody);
                        return true;
                    }
                }
                OfficialSendLogRepository.recordError(traceId, logType, "POST", url, json,
                        status, responseBody, "输入状态通知响应异常");
                log.warn("单聊输入状态通知失败, status: {}, body: {}", status, responseBody);
            } catch (Exception e) {
                OfficialSendLogRepository.recordError(traceId, logType, "POST", url, json,
                        status, responseBody, "输入状态通知失败: " + e.getMessage());
                log.warn("单聊输入状态通知失败", e);
            }
            return false;
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
        return prepareMessageAsync(request, groupFileUrl(groupOpenId), "群聊").thenCompose(effectiveRequest -> {
            if (effectiveRequest == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (effectiveRequest.getMsgId() == null && effectiveRequest.getEventId() == null) {
                activeRateLimiter.checkPerGroupActiveRate(groupOpenId);
            }
            return sendMessageAsync(groupMessageUrl(groupOpenId), effectiveRequest, "群聊")
                    .thenApply(response -> {
                        if (response != null) {
                            QQChatContentRecord.recordSentGroupMessage(groupOpenId, effectiveRequest, response.id(), response.refIdx(), response.timestamp());
                            if (effectiveRequest.getMsgId() == null && effectiveRequest.getEventId() == null && !OfficialGroups.allowProactiveMsg(groupOpenId)) {
                                OfficialGroups.setAllowProactiveMsg(groupOpenId, true);
                            }
                        }
                        if (response != null) {
                            return response.id() ;
                        } else {
                            return null;
                        }
                    });
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
        return prepareMessageAsync(request, null, "文字子频道").thenCompose(pendingRequest -> {
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
        return prepareMessageAsync(request, null, "频道私信").thenCompose(pendingRequest -> {
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
        var res = HttpService.deleteRequestDetailed(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
        boolean ok = res.status() >= 200 && res.status() < 300;
        if (!ok) {
            log.warn("撤回单聊消息失败, userOpenId: {}, messageId: {}, status: {}, body: {}",
                    userOpenId, messageId, res.status(), res.body());
        }
        recordRecallLog("单聊撤回", url, res, ok);
        return ok;
    }

    /**
     * 异步准备发送消息，暂停时将被动回复替换为维护图片
     *
     * @param request   原始消息体
     * @param uploadUrl 图片上传地址，频道使用图片 URL 时传入 null
     * @param logType   日志场景
     * @return 待发送消息体，主动消息被拦截或维护图片上传失败时结果为 null
     */
    private CompletableFuture<MessageBody> prepareMessageAsync(MessageBody request, String uploadUrl, String logType) {
        if (!emergencyPaused || (request != null && request.isMaintenanceReply())) {
            return CompletableFuture.completedFuture(request);
        }
        return ThreadManager.supplyAsync(() -> emergencyPauseRequestOrNull(request, uploadUrl, logType));
    }

    /**
     * 将暂停状态下的被动回复替换为维护图片，保留消息或事件来源
     *
     * @param request   原始消息体
     * @param uploadUrl 图片上传地址，频道使用图片 URL 时传入 null
     * @param logType   日志场景
     * @return 原始消息或维护图片消息，主动消息被拦截或图片上传失败时返回 null
     */
    private MessageBody emergencyPauseRequestOrNull(MessageBody request, String uploadUrl, String logType) {
        if (!emergencyPaused || (request != null && request.isMaintenanceReply())) {
            return request;
        }
        if (request == null || isActiveRequest(request)) {
            log.warn("{}主动消息已被应急暂停拦截", logType);
            return null;
        }
        RT rt = MessageBodyFactory.sourceOf(request.getMsgId(), request.getEventId());
        if (uploadUrl == null) {
            return bodyFactory.guildImage(MAINTENANCE.getData(), rt, MAINTENANCE.getText())
                    .toBuilder().maintenanceReply(true).build();
        }
        return mediaUploader.buildMaintenanceImageRequest(uploadUrl, rt, logType);
    }

    /**
     * 将单聊流式回复的暂停提示改为普通图片消息
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源
     * @return 维护图片消息 ID 的 Future，上传或发送失败时结果为 null
     */
    CompletableFuture<String> sendPrivateMaintenanceMessageAsync(String openId, RT rt) {
        return ThreadManager.supplyAsync(() -> mediaUploader.buildMaintenanceImageRequest(privateFileUrl(openId), rt, "单聊维护"))
                .thenCompose(request -> request == null ? CompletableFuture.completedFuture(null)
                        : sendPrivateMessageAsync(openId, request));
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
        var res = HttpService.deleteRequestDetailed(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
        boolean ok = res.status() >= 200 && res.status() < 300;
        if (!ok) {
            log.warn("撤回群聊消息失败, groupOpenId: {}, messageId: {}, status: {}, body: {}",
                    groupOpenId, messageId, res.status(), res.body());
        }
        recordRecallLog("群聊撤回", url, res, ok);
        return ok;
    }

    /**
     * 撤回接口的发送日志：SEND/RESPONSE/ERROR 三态共用一个 traceId。
     * 撤回本身是同步调用，落库统一丢给调度器异步执行，不阻塞调用线程。
     */
    private void recordRecallLog(String scene, String url, HttpService.GetResult result, boolean ok) {
        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> {
            String traceId = OfficialSendLogRepository.recordSend(scene, "DELETE", url, null);
            if (ok) {
                OfficialSendLogRepository.recordResponse(traceId, scene, "DELETE", url, null,
                        result.status(), result.body());
            } else {
                String reason = result.status() == 0 ? "请求异常: " + result.body() : "接口返回状态异常 " + result.status();
                OfficialSendLogRepository.recordError(traceId, scene, "DELETE", url, null,
                        result.status(), result.body(), reason);
            }
        });
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
        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            OfficialSendLogRepository.recordError(null, logType, "POST", url, null,
                    null, null, "消息序列化失败: " + e.getMessage());
            log.error("{}消息序列化失败: ", logType, e);
            return null;
        }

        if (!OfficialMessageSendNotifier.allowSend("POST", url, json)) return null;
        if (request.getMsgId() == null && request.getEventId() == null && logType.equals("群聊")) {
            activeRateLimiter.waitForActiveRateLimit();
        }

        var res = HttpService.postJsonDetailed(url, json,
                "Authorization", "QQBot " + tokenManager.getAccessToken());

        if (request.getContent() != null && !request.getContent().isBlank() && request.getContent().equalsIgnoreCase("Boop!")) {
            long ms2 = System.currentTimeMillis();
            long ms = ms2 - WhatFuckingPing.getAccessMs().get(request.getMsgId());
            GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), "消息发送耗时: " + ms + "ms, 链路: onCommand -> doMessageSend");
        }

        try {
            String traceId = OfficialSendLogRepository.recordSend(logType, "POST", url, json);
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
                        res.status(), res.body(), "请求状态异常或响应为空");
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
            String traceId = OfficialSendLogRepository.recordSend(logType, "POST", url, json);
            OfficialSendLogRepository.recordError(traceId, logType, "POST", url, json,
                    res.status(), res.body(), "响应解析失败: " + e.getMessage());
            log.error("{}消息响应解析失败: ", logType, e);
            return null;
        }
    }

    private record ChatResponse(String id, String timestamp, String refIdx) {}
}
