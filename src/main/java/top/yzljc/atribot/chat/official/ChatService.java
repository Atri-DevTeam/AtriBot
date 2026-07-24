package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.OfficialActiveMessageFailEvent;
import top.yzljc.atribot.event.events.OfficialC2CPushFailEvent;
import top.yzljc.atribot.function.official.ChatContentRecord;
import top.yzljc.atribot.platform.official.TokenManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author YZ_Ljc_
 * @ClassName ChatService
 * @Created_at 2026/05/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 * @Description
 * 聊天服务底层管道
 * 只负责 HTTP 发送、频控、消息序列号、媒体上传、流式助手等基础设施
 * 业务逻辑由 C2CChat / AsyncC2CChat 和 GroupChat / AsyncGroupChat 承载
 */
@Slf4j
@Getter
public class ChatService {

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

    /**
     * 同步发送单聊消息
     *
     * @param openId  用户 openId
     * @param request 消息体
     * @return 消息 ID，发送失败返回 null
     */
    public String sendPrivateMessage(String openId, MessageBody request) {
        return awaitSend(sendPrivateMessageAsync(openId, request), "单聊");
    }

    /**
     * 异步发送单聊消息，成功后自动记录到 ChatContentRecord
     *
     * @param openId  用户 openId
     * @param request 消息体
     * @return 消息 ID 的 Future
     */
    public CompletableFuture<String> sendPrivateMessageAsync(String openId, MessageBody request) {
        return sendMessageAsync(privateMessageUrl(openId), request, "单聊")
                .thenApply(messageId -> {
                    if (messageId != null) {
                        ChatContentRecord.recordSentC2CMessage(openId, request, messageId);
                    }
                    return messageId;
                });
    }

    /**
     * 同步发送群聊消息
     *
     * @param groupOpenId 群 openId
     * @param request     消息体
     * @return 消息 ID，发送失败返回 null
     */
    public String sendGroupMessage(String groupOpenId, MessageBody request) {
        return awaitSend(sendGroupMessageAsync(groupOpenId, request), "群聊");
    }

    /**
     * 异步发送群聊消息，成功后自动记录到 ChatContentRecord，并处理主动消息频控与白名单
     *
     * @param groupOpenId 群 openId
     * @param request     消息体
     * @return 消息 ID 的 Future
     */
    public CompletableFuture<String> sendGroupMessageAsync(String groupOpenId, MessageBody request) {
        if (request.getMsgId() == null && request.getEventId() == null) {
            activeRateLimiter.checkPerGroupActiveRate(groupOpenId);
        }
        return sendMessageAsync(groupMessageUrl(groupOpenId), request, "群聊")
                .thenApply(messageId -> {
                    if (messageId != null) {
                        ChatContentRecord.recordSentGroupMessage(groupOpenId, request, messageId);
                        if (request.getMsgId() == null && !OfficialGroups.isAllowedActiveMessages(groupOpenId)) {
                            OfficialGroups.setAllowedActiveMessage(groupOpenId, true);
                        }
                    }
                    return messageId;
                });
    }

    /**
     * 撤回单聊消息
     *
     * @param userOpenId 用户 openId
     * @param messageId  消息 ID
     */
    public void recallPrivateMessage(String userOpenId, String messageId) {
        String url = apiBaseUrl + "/v2/users/" + userOpenId + "/messages/" + messageId;
        try {
            HttpService.deleteRequestStr(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
        } catch (Exception e) {
            log.error("撤回单聊消息失败, unionOpenId: {}, messageId: {}", userOpenId, messageId, e);
        }
    }

    /**
     * 撤回群聊消息
     *
     * @param groupOpenId 群 openId
     * @param messageId   消息 ID
     */
    public void recallGroupMessage(String groupOpenId, String messageId) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages/" + messageId;
        try {
            HttpService.deleteRequestStr(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
        } catch (Exception e) {
            log.error("撤回群聊消息失败, groupOpenId: {}, messageId: {}", groupOpenId, messageId, e);
        }
    }

    /**
     * 获取群成员信息
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

    private CompletableFuture<String> sendMessageAsync(String url, MessageBody request, String logType) {
        return ThreadManager.supplyAsync(() -> doSendMessage(url, request, logType))
                .exceptionally(e -> {
                    log.error("{}消息异步发送任务失败, url: {}", logType, url, e);
                    return null;
                });
    }

    private String awaitSend(CompletableFuture<String> future, String logType) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{}消息发送等待被中断", logType, e);
            return null;
        } catch (ExecutionException e) {
            log.error("{}消息发送任务失败: {}", logType, e.getCause() != null ? e.getCause() : e);
            return null;
        }
    }

    private String doSendMessage(String url, MessageBody request, String logType) {
        if (request.getMsgId() == null && request.getEventId() == null) {
            activeRateLimiter.waitForActiveRateLimit();
        }

        try {
            String json = objectMapper.writeValueAsString(request);
            var res = HttpService.postJsonDetailed(url, json,
                    "Authorization", "QQBot " + tokenManager.getAccessToken());
            if (res.status() >= 200 && res.status() < 300 && res.body() != null && !res.body().isBlank()) {
                JsonNode result = objectMapper.readTree(res.body());
                JsonNode idNode = result.get("id");
                if (idNode != null && !idNode.asText().isBlank()) {
                    return idNode.asText();
                }
                log.error("{}消息发送失败, 返回无 id: {}", logType, result);
            } else {
                log.error("{}消息发送失败, status: {}, body: {}", logType, res.status(), res.body());
                if (res.body() != null) {
                    try {
                        JsonNode err = objectMapper.readTree(res.body());
                        int code = err.path("err_code").asInt(0);
                        String msg = err.path("message").asText(null);
                        if (logType.equals("群聊") && url.contains("/groups/")) {
                            String gid = url.substring(url.indexOf("/groups/") + 8, url.indexOf("/messages"));
                            EventManager.getInstance().callEvent(new OfficialActiveMessageFailEvent(gid, code, msg));
                        }
                        if (logType.equals("单聊") && url.contains("users")) {
                            String userId = url.substring(url.indexOf("/users/") + 7, url.indexOf("/messages"));
                            EventManager.getInstance().callEvent(new OfficialC2CPushFailEvent(userId, code, msg));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return null;
        } catch (JsonProcessingException e) {
            log.error("{}消息序列化失败: ", logType, e);
            return null;
        }
    }
}
