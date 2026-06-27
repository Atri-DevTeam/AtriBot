package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.PermissionType;
import top.yzljc.atribot.chat.official.media.GroupMessageType;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.OfficialActiveMessageFailEvent;
import top.yzljc.atribot.function.official.ChatContentRecord;
import top.yzljc.atribot.platform.official.TokenManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ChatService {

    private final String apiBaseUrl;
    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, AtomicInteger> msgSeqCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    // 60 QPM
    private static final int ACTIVE_QPM_LIMIT = 60;
    private static final long WINDOW_MS = 60_000;
    private final Deque<Long> activeTimestamps = new ConcurrentLinkedDeque<>();

    public ChatService(String apiBaseUrl, TokenManager tokenManager) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
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

    private String privateMessageUrl(String openId) {
        return apiBaseUrl + "/v2/users/" + openId + "/messages";
    }

    private String groupMessageUrl(String groupOpenId) {
        return apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages";
    }

    private String privateFileUrl(String openId) {
        return apiBaseUrl + "/v2/users/" + openId + "/files";
    }

    private String groupFileUrl(String groupOpenId) {
        return apiBaseUrl + "/v2/groups/" + groupOpenId + "/files";
    }

    private MessageBody textRequest(String text) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(text)
                .build();
    }

    private MessageBody textRefRequest(String text, String refIdx) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(text)
                .messageReference(Map.of("message_id", refIdx))
                .build();
    }

    private MessageBody markdownRequest(Markdown markdown) {
        return markdownRequest(markdown.getText(), null, null, null);
    }

    private MessageBody markdownRequest(Markdown markdown, Object keyboard) {
        return markdownRequest(markdown.getText(), keyboard, null, null);
    }

    private MessageBody markdownRequest(String markdownContent, Object keyboard, String msgId, String eventId) {
        MessageBody.MessageBodyBuilder builder = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .markdown(buildMarkdown(markdownContent));
        if (keyboard != null) {
            builder.keyboard(keyboard);
        }
        if (msgId != null) {
            builder.msgId(msgId).msgSeq(getNextMsgSeq(msgId));
        }
        if (eventId != null) {
            builder.eventId(eventId);
        }
        return builder.build();
    }

    private MessageBody replyTextRequest(String msgId, String text) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .content(text)
                .build();
    }

    private MessageBody mediaRequest(String fileInfo, String msgId) {
        Map<String, Object> mediaObj = new HashMap<>();
        mediaObj.put("file_info", fileInfo);

        MessageBody.MessageBodyBuilder builder = MessageBody.builder()
                .msgType(GroupMessageType.MEDIA.getValue())
                .media(mediaObj);
        if (msgId != null) {
            builder.msgId(msgId).msgSeq(getNextMsgSeq(msgId));
        }
        return builder.build();
    }

    private String atMarkdown(String userOpenId, Markdown markdown) {
        return Markdown.at(userOpenId) + "\n" + markdown.getText();
    }

    public String sendPrivateMessage(String openId, MessageBody request) {
        return awaitSend(sendPrivateMessageAsync(openId, request), "单聊");
    }

    public CompletableFuture<String> sendPrivateMessageAsync(String openId, MessageBody request) {
        return sendMessageAsync(privateMessageUrl(openId), request, "单聊")
                .thenApply(messageId -> {
                    if (messageId != null) {
                        ChatContentRecord.recordSentC2CMessage(openId, request, messageId);
                    }
                    return messageId;
                });
    }

    public String sendGroupMessage(String groupOpenId, MessageBody request) {
        return awaitSend(sendGroupMessageAsync(groupOpenId, request), "群聊");
    }

    public CompletableFuture<String> sendGroupMessageAsync(String groupOpenId, MessageBody request) {
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

    public String sendActiveGroupTextMessage(String groupOpenId, String text) {
        return sendGroupMessage(groupOpenId, textRequest(text));
    }

    public String sendActiveGroupRefTextMessage(String groupOpenId, String text, String refIdx) {
        return sendGroupMessage(groupOpenId, textRefRequest(text, refIdx));
    }

    public CompletableFuture<String> sendActiveGroupTextMessageAsync(String groupOpenId, String text) {
        return sendGroupMessageAsync(groupOpenId, textRequest(text));
    }

    public String sendActivePrivateTextMessage(String openId, String text) {
        return sendPrivateMessage(openId, textRequest(text));
    }

    public CompletableFuture<String> sendActivePrivateTextMessageAsync(String openId, String text) {
        return sendPrivateMessageAsync(openId, textRequest(text));
    }

    public String sendActiveGroupMarkdownMessage(String groupOpenId, Markdown markdown) {
        return sendGroupMessage(groupOpenId, markdownRequest(markdown));
    }

    public CompletableFuture<String> sendActiveGroupMarkdownMessageAsync(String groupOpenId, Markdown markdown) {
        return sendGroupMessageAsync(groupOpenId, markdownRequest(markdown));
    }

    public String sendActiveGroupMarkdownMessage(String groupOpenId, Markdown markdown, Object keyboard) {
        return sendGroupMessage(groupOpenId, markdownRequest(markdown, keyboard));
    }

    public CompletableFuture<String> sendActiveGroupMarkdownMessageAsync(String groupOpenId, Markdown markdown, Object keyboard) {
        return sendGroupMessageAsync(groupOpenId, markdownRequest(markdown, keyboard));
    }

    public String sendActivePrivateMarkdownMessage(String openId, Markdown markdown) {
        return sendPrivateMessage(openId, markdownRequest(markdown));
    }

    public CompletableFuture<String> sendActivePrivateMarkdownMessageAsync(String openId, Markdown markdown) {
        return sendPrivateMessageAsync(openId, markdownRequest(markdown));
    }

    public String sendActivePrivateMarkdownMessage(String openId, Markdown markdown, Object keyboard) {
        return sendPrivateMessage(openId, markdownRequest(markdown, keyboard));
    }

    public CompletableFuture<String> sendActivePrivateMarkdownMessageAsync(String openId, Markdown markdown, Object keyboard) {
        return sendPrivateMessageAsync(openId, markdownRequest(markdown, keyboard));
    }

    public String sendActivePrivateImageMessage(String openId, ImageType type, String value) {
        MessageBody request = buildImageRequest(privateFileUrl(openId), type, value, "单聊主动", null);
        return request == null ? null : sendPrivateMessage(openId, request);
    }

    public CompletableFuture<String> sendActivePrivateImageMessageAsync(String openId, ImageType type, String value) {
        return ThreadManager.supplyAsync(() -> buildImageRequest(privateFileUrl(openId), type, value, "单聊主动", null))
                .thenCompose(request -> request == null ? CompletableFuture.completedFuture(null) : sendPrivateMessageAsync(openId, request));
    }

    public String sendActiveGroupImageMessage(String groupOpenId, ImageType type, String value) {
        MessageBody request = buildImageRequest(groupFileUrl(groupOpenId), type, value, "群聊主动", null);
        return request == null ? null : sendGroupMessage(groupOpenId, request);
    }

    public CompletableFuture<String> sendActiveGroupImageMessageAsync(String groupOpenId, ImageType type, String value) {
        return ThreadManager.supplyAsync(() -> buildImageRequest(groupFileUrl(groupOpenId), type, value, "群聊主动", null))
                .thenCompose(request -> request == null ? CompletableFuture.completedFuture(null) : sendGroupMessageAsync(groupOpenId, request));
    }

    public String replyGroupTextMessage(String groupOpenId, String msgId, String replyText) {
        return sendGroupMessage(groupOpenId, replyTextRequest(msgId, replyText));
    }

    public CompletableFuture<String> replyGroupTextMessageAsync(String groupOpenId, String msgId, String replyText) {
        return sendGroupMessageAsync(groupOpenId, replyTextRequest(msgId, replyText));
    }

    public String replyGroupMarkdownMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown) {
        return sendGroupMessage(groupOpenId, markdownRequest(atMarkdown(userOpenId, markdown), null, msgId, null));
    }

    public CompletableFuture<String> replyGroupMarkdownMessageAsync(String groupOpenId, String userOpenId, String msgId, Markdown markdown) {
        return sendGroupMessageAsync(groupOpenId, markdownRequest(atMarkdown(userOpenId, markdown), null, msgId, null));
    }

    public String replyPrivateTextMessage(String openId, String msgId, String replyText) {
        return sendPrivateMessage(openId, replyTextRequest(msgId, replyText));
    }

    public CompletableFuture<String> replyPrivateTextMessageAsync(String openId, String msgId, String replyText) {
        return sendPrivateMessageAsync(openId, replyTextRequest(msgId, replyText));
    }

    public String replyPrivateMarkdownMessage(String openId, String msgId, Markdown markdown) {
        return sendPrivateMessage(openId, markdownRequest(markdown.getText(), null, msgId, null));
    }

    public CompletableFuture<String> replyPrivateMarkdownMessageAsync(String openId, String msgId, Markdown markdown) {
        return sendPrivateMessageAsync(openId, markdownRequest(markdown.getText(), null, msgId, null));
    }

    public String replyGroupEvent(String groupOpenId, String memberOpenId, String eventId, Markdown markdown) {
        return sendGroupMessage(groupOpenId, markdownRequest(markdown.getText(), null, null, eventId));
    }

    public CompletableFuture<String> replyGroupEventAsync(String groupOpenId, String memberOpenId, String eventId, Markdown markdown) {
        return sendGroupMessageAsync(groupOpenId, markdownRequest(markdown.getText(), null, null, eventId));
    }

    public String replyGroupEvent(String groupOpenId, String memberOpenId, String eventId, Markdown markdown, Object buttons) {
        return sendGroupMessage(groupOpenId, markdownRequest(markdown.getText(), buttons, null, eventId));
    }

    public CompletableFuture<String> replyGroupEventAsync(String groupOpenId, String memberOpenId, String eventId, Markdown markdown, Object buttons) {
        return sendGroupMessageAsync(groupOpenId, markdownRequest(markdown.getText(), buttons, null, eventId));
    }

    public Object buildButtonKeyboard(List<List<Button>> layout) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (List<Button> rowBtns : layout) {
            List<Map<String, Object>> buttons = new ArrayList<>();

            for (Button btn : rowBtns) {
                Map<String, Object> action = new HashMap<>();
                action.put("type", btn.getActionType().getCode());
                action.put("data", btn.getData());
                action.put("enter", btn.isEnter());
                action.put("unsupport_tips", "当前客户端版本不支持此按钮");
                if (btn.isReply()) {
                    action.put("reply", true);
                }

                Map<String, Object> permission = new HashMap<>();
                permission.put("type", btn.getPermissionType().getCode());
                if (btn.getPermissionType() == PermissionType.SPECIFIC_USER
                        && !btn.getAllowedOpenIds().isEmpty()) {
                    permission.put("specify_user_ids", btn.getAllowedOpenIds());
                }
                action.put("permission", permission);

                Map<String, Object> renderData = new HashMap<>();
                renderData.put("label", btn.getDisplayText());
                renderData.put("visited_label", btn.getVisitedDisplayText());
                renderData.put("style", btn.getStyle().getCode());

                Map<String, Object> button = new HashMap<>();
                button.put("id", btn.getButtonId());
                button.put("render_data", renderData);
                button.put("action", action);

                buttons.add(button);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("buttons", buttons);
            rows.add(row);
        }
        Map<String, Object> keyboard = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        content.put("rows", rows);
        keyboard.put("content", content);
        return keyboard;
    }

    public String replyPrivateImageMessage(String openId, String msgId, ImageType type, String value) {
        MessageBody request = buildImageRequest(privateFileUrl(openId), type, value, "单聊", msgId);
        return request == null ? null : sendPrivateMessage(openId, request);
    }

    public CompletableFuture<String> replyPrivateImageMessageAsync(String openId, String msgId, ImageType type, String value) {
        return ThreadManager.supplyAsync(() -> buildImageRequest(privateFileUrl(openId), type, value, "单聊", msgId))
                .thenCompose(request -> request == null ? CompletableFuture.completedFuture(null) : sendPrivateMessageAsync(openId, request));
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyGroupImageMessage(String groupOpenId, String msgId, ImageType type, String value) {
        MessageBody request = buildImageRequest(groupFileUrl(groupOpenId), type, value, "群聊", msgId);
        return request == null ? null : sendGroupMessage(groupOpenId, request);
    }

    public CompletableFuture<String> replyGroupImageMessageAsync(String groupOpenId, String msgId, ImageType type, String value) {
        return ThreadManager.supplyAsync(() -> buildImageRequest(groupFileUrl(groupOpenId), type, value, "群聊", msgId))
                .thenCompose(request -> request == null ? CompletableFuture.completedFuture(null) : sendGroupMessageAsync(groupOpenId, request));
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyGroupFileMessage(String groupOpenId, String msgId, int type, String value) {
        MessageBody request = buildFileRequest(groupFileUrl(groupOpenId), type, value, "文件发送", msgId);
        return request == null ? null : sendGroupMessage(groupOpenId, request);
    }

    public CompletableFuture<String> replyGroupFileMessageAsync(String groupOpenId, String msgId, int type, String value) {
        return ThreadManager.supplyAsync(() -> buildFileRequest(groupFileUrl(groupOpenId), type, value, "文件发送", msgId))
                .thenCompose(request -> request == null ? CompletableFuture.completedFuture(null) : sendGroupMessageAsync(groupOpenId, request));
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyPrivateMarkdownMessage(String openId, String msgId, Markdown markdown, Object keyboard) {
        return sendPrivateMessage(openId, markdownRequest(markdown.getText(), keyboard, msgId, null));
    }

    public CompletableFuture<String> replyPrivateMarkdownMessageAsync(String openId, String msgId, Markdown markdown, Object keyboard) {
        return sendPrivateMessageAsync(openId, markdownRequest(markdown.getText(), keyboard, msgId, null));
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyGroupMarkdownMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown, Object keyboard) {
        return sendGroupMessage(groupOpenId, markdownRequest(atMarkdown(userOpenId, markdown), keyboard, msgId, null));
    }

    public CompletableFuture<String> replyGroupMarkdownMessageAsync(String groupOpenId, String userOpenId, String msgId, Markdown markdown, Object keyboard) {
        return sendGroupMessageAsync(groupOpenId, markdownRequest(atMarkdown(userOpenId, markdown), keyboard, msgId, null));
    }

    public void recallPrivateMessage(String userOpenId, String messageId) {
        String url = apiBaseUrl + "/v2/users/" + userOpenId + "/messages/" + messageId;
        try {
            HttpService.deleteRequestStr(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
        } catch (Exception e) {
            log.error("撤回单聊消息失败, unionOpenId: {}, messageId: {}", userOpenId, messageId, e);
        }
    }

    public void recallGroupMessage(String groupOpenId, String messageId) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages/" + messageId;
        try {
            HttpService.deleteRequestStr(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
        } catch (Exception e) {
            log.error("撤回群聊消息失败, groupOpenId: {}, messageId: {}", groupOpenId, messageId, e);
        }
    }

    private String uploadImageFile(String uploadUrl, ImageType type, String value, String logLabel) {
        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("file_type", 1);
        uploadData.put(type.getDataKey(), value);
        uploadData.put("srv_send_msg", false);
        return uploadAndGetFileInfo(uploadUrl, uploadData, logLabel);
    }

    private MessageBody buildImageRequest(String uploadUrl, ImageType type, String value, String logLabel, String msgId) {
        String fileInfo = uploadImageFile(uploadUrl, type, value, logLabel);
        if (fileInfo == null) return null;

        return mediaRequest(fileInfo, msgId);
    }

    private MessageBody buildFileRequest(String uploadUrl, int type, String value, String logLabel, String msgId) {
        String fileInfo = uploadFile(uploadUrl, type, value, logLabel);
        if (fileInfo == null) return null;

        return mediaRequest(fileInfo, msgId);
    }

    private String uploadFile(String uploadUrl, int type, String value, String logLabel) {
        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("file_type", type);
        uploadData.put("url", value);
        uploadData.put("srv_send_msg", false);
        return uploadAndGetFileInfo(uploadUrl, uploadData, logLabel);
    }

    private String uploadAndGetFileInfo(String uploadUrl, Map<String, Object> uploadData, String logLabel) {
        try {
            String uploadJson = objectMapper.writeValueAsString(uploadData);
            String uploadRes = HttpService.postJsonForString(uploadUrl, uploadJson,
                    "Authorization", "QQBot " + tokenManager.getAccessToken());

            if (uploadRes == null || uploadRes.isBlank()) {
                log.error("{}上传失败，服务器返回为空", logLabel);
                return null;
            }

            JsonNode resNode = objectMapper.readTree(uploadRes);
            if (!resNode.has("file_info")) {
                log.error("{}上传失败，未返回 file_info: {}", logLabel, uploadRes);
                return null;
            }
            return resNode.get("file_info").asText();
        } catch (Exception e) {
            log.error("{}上传异常", logLabel, e);
            return null;
        }
    }

    private Map<String, Object> buildMarkdown(Markdown markdown) {
        return buildMarkdown(markdown.getText());
    }

    private Map<String, Object> buildMarkdown(String markdownContent) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", markdownContent);
        return markdownObj;
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

    private void waitForActiveRateLimit() {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        // 清理过期记录
        while (true) {
            Long oldest = activeTimestamps.peekFirst();
            if (oldest == null || oldest >= cutoff) break;
            activeTimestamps.pollFirst();
        }
        // 超出限制则等待最旧记录过期
        if (activeTimestamps.size() >= ACTIVE_QPM_LIMIT) {
            Long oldest = activeTimestamps.peekFirst();
            if (oldest != null) {
                long waitMs = oldest + WINDOW_MS - now + 50;
                if (waitMs > 0) {
                    log.info("主动消息已达 {} QPM 限制，等待 {}ms", ACTIVE_QPM_LIMIT, waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    // 等待后再次清理
                    long newNow = System.currentTimeMillis();
                    long newCutoff = newNow - WINDOW_MS;
                    while (true) {
                        Long o = activeTimestamps.peekFirst();
                        if (o == null || o >= newCutoff) break;
                        activeTimestamps.pollFirst();
                    }
                }
            }
        }
        activeTimestamps.offerLast(System.currentTimeMillis());
    }

    private String doSendMessage(String url, MessageBody request, String logType) {
        // 主动消息（无 msgId ≠ 被动回复）需遵守 60 QPM 频控
        if (request.getMsgId() == null) {
            waitForActiveRateLimit();
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