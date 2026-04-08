package top.yzljc.qqbot.chat.impl;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.service.request.PostRequest;
import top.yzljc.qqbot.service.request.RequestType;
import top.yzljc.qqbot.service.tools.RM;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.utils.Logger;

import java.util.*;

public class MessageUtils {
    private static final String FAKE_UIN = "3199590352";
    private static final String FAKE_NAME = "YZ_Ljc_";
    private static final MessageSegment AT_GAP = new MessageSegment("text", Map.of("text", " "));

    public static long groupMessage(long groupId, Collection<MessageSegment> data) {
        if (data == null || data.isEmpty()) return 0L;
        try {
            JsonNode resp = PostRequest.getPostResult(
                    RequestType.SEND_GROUP_MSG,
                    Map.of("group_id", groupId, "message", data)
            );

            long messageId = resp.path("data").path("message_id").asLong(0L);

            if (messageId != 0L) {
                RM.recordLastMsg(groupId, messageId);
                return messageId;
            }

            Logger.error("消息发送失败，返回内容: {}", resp);
        } catch (Exception e) {
            Logger.error("推送异常：{}", e.getMessage(), e);
        }

        return 0L;
    }

    public static long privateMessage(long userId, Collection<MessageSegment> data) {
        if (data == null || data.isEmpty()) return 0L;
        try {
            JsonNode resp = PostRequest.getPostResult(
                    RequestType.SEND_PRIVATE_MSG,
                    Map.of("user_id", userId, "message", data)
            );
            long messageId = resp.path("data").path("message_id").asLong(0L);
            if (messageId != 0L) return messageId;
            Logger.error("私聊消息发送失败，返回内容: {}", resp);
        } catch (Exception e) {
            Logger.error("私聊推送异常：{}", e.getMessage(), e);
        }
        return 0L;
    }

    public static long privateMessage(long userId, String text) {
        return privateMessage(userId, List.of(textSegment(text)));
    }

    public static long privateMessage(long userId, String imgData, ImageType type) {
        if (imgData == null || imgData.isBlank() || type == null) return 0L;
        return privateMessage(userId, List.of(imageSegment(imgData, type)));
    }

    public static long replyPrivateMessage(long userId, long messageId, String text) {
        return replyPrivateMessage(
                userId,
                messageId,
                List.of(textSegment(text))
        );
    }

    public static long replyPrivateMessage(long userId, long messageId, Collection<MessageSegment> messageSegments) {
        List<MessageSegment> replayContent = new ArrayList<>();
        replayContent.add(replySegment(messageId));
        if (messageSegments != null && !messageSegments.isEmpty()) {
            replayContent.addAll(messageSegments);
        }
        return privateMessage(userId, replayContent);
    }

    public static long chatMessage(long userId, long groupId, String text, boolean whetherAt) {
        if (!whetherAt) {
            return groupMessage(groupId, List.of(textSegment(text)));
        }
        return groupMessage(groupId, buildWithAtPrefix(userId, List.of(textSegment(text))));
    }

    public static long chatMessage(long userId, long groupId, Collection<MessageSegment> data, boolean whetherAt) {
        if (!whetherAt) return groupMessage(groupId, data);
        return groupMessage(groupId, buildWithAtPrefix(userId, data));
    }

    public static long chatMessage(long userId, long groupId, String imgData, ImageType type, boolean whetherAt) {
        if (!whetherAt) return sendSingleImageGroupMessage(groupId, imgData, type);
        if (imgData == null || imgData.isBlank() || type == null) return 0L;
        return groupMessage(groupId, buildWithAtPrefix(userId, List.of(imageSegment(imgData, type))));
    }

    public static long groupTextImageMessage(long groupId, String text, String imgData, ImageType type) {
        if ((text == null || text.isBlank()) && (imgData == null || imgData.isBlank())) return 0L;
        List<MessageSegment> segments = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            segments.add(textSegment(text));
        }
        if (imgData != null && !imgData.isBlank() && type != null) {
            segments.add(imageSegment(imgData, type));
        }
        return groupMessage(groupId, segments);
    }

    public static long replyMessage(long userId, long groupId, long messageId, boolean whetherAt, String text) {
        return replyMessage(
                userId,
                groupId,
                messageId,
                whetherAt,
                List.of(textSegment(text))
        );
    }

    public static long replyMessage(long userId, long groupId, long messageId, boolean whetherAt, Collection<MessageSegment> messageSegments) {
        List<MessageSegment> replayContent = whetherAt ? buildWithAtPrefix(userId, null) : new ArrayList<>();
        replayContent.add(replySegment(messageId));
        if (messageSegments != null && !messageSegments.isEmpty()) {
            replayContent.addAll(messageSegments);
        }
        return groupMessage(groupId, replayContent);
    }

    public static long sendPrivateForwardMessage(long userId, Collection<MessageSegment> nodes, String title, String summary, String... textVars) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("user_id", userId);
            payload.put("messages", nodes);
            payload.put("news", buildNews(textVars));

            payload.put("source", title);
            payload.put("summary", summary);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_PRIVATE_FORWARD_MSG, payload);
            return resp.path("data").path("message_id").asLong(0L);
        } catch (Exception e) {
            Logger.warn("发送好友转发消息失败: {}", e.getMessage());
        }
        return 0L;
    }

    public static long sendGroupForwardMessage(long groupId, Collection<MessageSegment> nodes, String title, String summary, String... textVars) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("group_id", groupId);
            payload.put("messages", nodes);
            payload.put("news", buildNews(textVars));

            payload.put("source", title);
            payload.put("summary", summary);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_GROUP_FORWARD_MSG, payload);
            long forwardMessageId = resp.path("data").path("message_id").asLong(0L);
            if (forwardMessageId != 0L) {
                RM.recordLastMsg(groupId, forwardMessageId);
            }
            return forwardMessageId;

        } catch (Exception e) {
            Logger.warn("发送群转发消息失败: {}", e.getMessage());
        }
        return 0L;
    }

    public static long forwardSingleGroupMsg(long groupId, long messageId){
        Map<String, Object> forwardMsg = new HashMap<>();
        forwardMsg.put("message_id", messageId);
        forwardMsg.put("group_id", groupId);
        JsonNode result = PostRequest.getPostResult(RequestType.FORWARD_SINGLE_MSG, forwardMsg);
        if (result != null) {
            long newMessageId = result.path("data").path("message_id").asLong(0L);
            if (newMessageId != 0L) {
                RM.recordLastMsg(groupId, newMessageId);
                return newMessageId;
            }
        }
        return messageId;
    }

    public static long sendSingleImageGroupMessage(long groupId, String imageData, ImageType type) {
        if (imageData == null || imageData.isBlank() || type == null) return 0L;
        return groupMessage(groupId, List.of(imageSegment(imageData, type)));
    }

    public static void atUser(long userId, long groupId, String text) {
        List<MessageSegment> replayContent = buildWithAtPrefix(userId, List.of(textSegment(text)));
        groupMessage(groupId, replayContent);
    }

    public static MessageSegment createTextNodeSegment(String text) {
        return createTextNodeSegment(text, FAKE_UIN, FAKE_NAME);
    }

    public static MessageSegment createTextNodeSegment(String text, String uin, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("uin", uin);
        data.put("name", name);
        data.put("content", List.of(textSegment(text)));
        return new MessageSegment("node", data);
    }

    public static MessageSegment createImageNodeSegment(String url) {
        return createImageNodeSegment(url, FAKE_UIN, FAKE_NAME);
    }

    public static MessageSegment createImageNodeSegment(String url, String uin, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("uin", uin);
        data.put("name", name);
        data.put("content", List.of(new MessageSegment("image", Map.of("file", url))));
        return new MessageSegment("node", data);
    }

    private static MessageSegment textSegment(String text) {
        return new MessageSegment("text", Map.of("text", text));
    }

    private static MessageSegment atSegment(long userId) {
        return new MessageSegment("at", Map.of("qq", userId));
    }

    private static MessageSegment replySegment(long messageId) {
        return new MessageSegment("reply", Map.of("id", messageId));
    }

    private static MessageSegment imageSegment(String imageData, ImageType type) {
        String value = imageData.trim();
        Map<String, Object> imageDataMap = switch (type) {
            case URL -> Map.of("url", value);
            case FILE -> Map.of("file", value.startsWith("file://") ? value : "file://" + value);
            case BASE64 -> Map.of("file", value.startsWith("base64://") ? value : "base64://" + value);
        };
        return new MessageSegment("image", imageDataMap);
    }

    private static List<MessageSegment> buildWithAtPrefix(long userId, Collection<MessageSegment> content) {
        List<MessageSegment> messageSegments = new ArrayList<>();
        messageSegments.add(atSegment(userId));
        messageSegments.add(AT_GAP);
        if (content != null && !content.isEmpty()) {
            messageSegments.addAll(content);
        }
        return messageSegments;
    }

    private static List<Map<String, String>> buildNews(String... textVars) {
        List<Map<String, String>> news = new ArrayList<>();
        if (textVars == null) return news;
        for (String textVar : textVars) {
            if (textVar != null && !textVar.isEmpty()) {
                news.add(Map.of("text", textVar));
            }
        }
        return news;
    }

    public static void handleGroupRequest(boolean approve, String flag, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("flag", flag);
        payload.put("sub_type", "add");
        payload.put("approve", approve);
        if (reason != null) {
            payload.put("reason", reason);
        }
        PostRequest.sendPost(RequestType.HANDLE_GROUP_PENDING_REQUEST, payload);
        Logger.info("已{}群请求，flag: {}", approve ? "批准" : "拒绝", flag);
    }

    public static void recallMessage(long messageId) {
        PostRequest.sendSimplePost(RequestType.RECALL_MESSAGE, "message_id", messageId);
    }

    public enum ImageType {
        URL, BASE64, FILE
    }
}
