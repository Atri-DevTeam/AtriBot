package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.Ark23;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.MessageBody;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Isolated group-message sender for one additional QQ bot.
 *
 * <p>This class only calls the group message POST endpoint and group message
 * DELETE endpoint. It does not call AtriBot's primary ChatService, event bus,
 * moderation APIs or database repositories.</p>
 */
@Slf4j
public final class GroupBotChat {

    @Getter
    private final String apiBaseUrl;
    private final Supplier<String> accessTokenProvider;
    private final GroupBotTransport transport;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GroupBotMessageFactory messageFactory;
    private final GroupBotRateLimiter activeRateLimiter = new GroupBotRateLimiter();
    private final Cache<String, AtomicInteger> msgSeqCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    GroupBotChat(String apiBaseUrl, GroupBotTokenManager tokenManager) {
        this(apiBaseUrl, tokenManager::getAccessToken, GroupBotTransport.live());
    }

    GroupBotChat(String apiBaseUrl, Supplier<String> accessTokenProvider, GroupBotTransport transport) {
        this.apiBaseUrl = stripTrailingSlash(requireNonBlank(apiBaseUrl, "apiBaseUrl"));
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.messageFactory = new GroupBotMessageFactory(this::nextMessageSequence);
    }

    public CompletableFuture<String> sendMessage(String groupOpenId, String content) {
        return sendMessage(groupOpenId, messageFactory.text(content));
    }

    public CompletableFuture<String> sendMessage(String groupOpenId, Markdown markdown) {
        return sendMessage(groupOpenId, messageFactory.markdown(markdown, null, null, null));
    }

    public CompletableFuture<String> sendMessage(String groupOpenId, Markdown markdown, Object keyboard) {
        return sendMessage(groupOpenId, messageFactory.markdown(markdown, keyboard, null, null));
    }

    public CompletableFuture<String> sendMessage(String groupOpenId, Ark23 ark) {
        return sendMessage(groupOpenId, messageFactory.ark(ark, null, null));
    }

    /** Allows additional reusable message models without coupling to primary ChatService. */
    public CompletableFuture<String> sendMessage(String groupOpenId, MessageBody request) {
        String targetGroup = requireNonBlank(groupOpenId, "groupOpenId");
        MessageBody body = Objects.requireNonNull(request, "request");
        return ThreadManager.supplyAsync(() -> sendNow(targetGroup, body));
    }

    public CompletableFuture<String> replyMessage(String groupOpenId, String messageId, String content) {
        return sendMessage(groupOpenId, messageFactory.replyText(
                requireNonBlank(messageId, "messageId"), content));
    }

    public CompletableFuture<String> replyMessage(
            String groupOpenId, String messageId, Markdown markdown) {
        return sendMessage(groupOpenId, messageFactory.markdown(
                markdown, null, requireNonBlank(messageId, "messageId"), null));
    }

    public CompletableFuture<String> replyMessage(
            String groupOpenId, String messageId, Markdown markdown, Object keyboard) {
        return sendMessage(groupOpenId, messageFactory.markdown(
                markdown, keyboard, requireNonBlank(messageId, "messageId"), null));
    }

    public CompletableFuture<String> replyMessage(
            String groupOpenId, String messageId, Ark23 ark) {
        return sendMessage(groupOpenId, messageFactory.ark(
                ark, requireNonBlank(messageId, "messageId"), null));
    }

    public CompletableFuture<String> replyEventMessage(
            String groupOpenId, String eventId, String content) {
        return sendMessage(groupOpenId, messageFactory.eventText(
                requireNonBlank(eventId, "eventId"), content));
    }

    public CompletableFuture<String> replyEventMessage(
            String groupOpenId, String eventId, Markdown markdown) {
        return sendMessage(groupOpenId, messageFactory.markdown(
                markdown, null, null, requireNonBlank(eventId, "eventId")));
    }

    public CompletableFuture<String> replyEventMessage(
            String groupOpenId, String eventId, Markdown markdown, Object keyboard) {
        return sendMessage(groupOpenId, messageFactory.markdown(
                markdown, keyboard, null, requireNonBlank(eventId, "eventId")));
    }

    public CompletableFuture<String> replyEventMessage(
            String groupOpenId, String eventId, Ark23 ark) {
        return sendMessage(groupOpenId, messageFactory.ark(
                ark, null, requireNonBlank(eventId, "eventId")));
    }

    public boolean recallMessage(String groupOpenId, String messageId) {
        String url = groupMessageUrl(requireNonBlank(groupOpenId, "groupOpenId"))
                + "/" + requireNonBlank(messageId, "messageId");
        String accessToken = accessTokenProvider.get();
        if (accessToken == null || accessToken.isBlank()) {
            log.error("QQ 群聊消息撤回失败：Token 为空");
            return false;
        }
        GroupBotTransport.Response response = transport.delete(url, accessToken);
        if (!response.successful()) {
            log.warn("QQ 群聊消息撤回失败: groupOpenId={}, messageId={}, status={}, body={}",
                    groupOpenId, messageId, response.status(), response.body());
        }
        return response.successful();
    }

    private String sendNow(String groupOpenId, MessageBody request) {
        if (request.getMsgId() == null && request.getEventId() == null) {
            activeRateLimiter.awaitPermit();
        }

        String accessToken = accessTokenProvider.get();
        if (accessToken == null || accessToken.isBlank()) {
            log.error("QQ 群聊消息发送失败：Token 为空");
            return null;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("QQ 群聊消息序列化失败", e);
            return null;
        }

        GroupBotTransport.Response response = transport.post(groupMessageUrl(groupOpenId), json, accessToken);
        if (!response.successful() || response.body() == null || response.body().isBlank()) {
            log.warn("QQ 群聊消息发送失败: groupOpenId={}, status={}, body={}",
                    groupOpenId, response.status(), response.body());
            return null;
        }

        try {
            JsonNode result = objectMapper.readTree(response.body());
            String messageId = result.path("id").asText(null);
            if (messageId == null || messageId.isBlank()) {
                log.warn("QQ 群聊消息发送失败，响应中没有消息 ID: {}", result);
                return null;
            }
            return messageId;
        } catch (Exception e) {
            log.error("QQ 群聊消息响应解析失败", e);
            return null;
        }
    }

    private String groupMessageUrl(String groupOpenId) {
        return apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages";
    }

    private int nextMessageSequence(String messageId) {
        try {
            return msgSeqCache.get(messageId, () -> new AtomicInteger()).incrementAndGet();
        } catch (Exception e) {
            log.warn("生成 QQ 群聊消息序号失败: messageId={}", messageId, e);
            return 1;
        }
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
