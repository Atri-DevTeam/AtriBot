package top.yzljc.atribot.chat.napcat.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;
import top.yzljc.atribot.utils.tools.RM;

import java.util.*;

@Slf4j
public class MessageUtils {
    private static final String FAKE_UIN = "3889798968";
    private static final String FAKE_NAME = "亚托莉喵";
    private static final MessageSegment AT_GAP = new MessageSegment("text", Map.of("text", " "));

    public static String groupMessage(String groupId, Collection<MessageSegment> data) {
        if (data == null || data.isEmpty()) return null;
        try {
            JsonNode resp = PostRequest.getPostResult(
                    RequestType.SEND_GROUP_MSG,
                    Map.of("group_id", groupId, "message", data)
            );

            String messageId = resp.path("data").path("message_id").asText(null);

            if (messageId != null && !messageId.isEmpty()) {
                RM.recordLastMsg(groupId, messageId);
                return messageId;
            }

            log.error("消息发送失败，返回内容: {}", resp);
        } catch (Exception e) {
            log.error("推送异常：{}", e.getMessage(), e);
        }

        return null;
    }

    public static String privateMessage(String userId, Collection<MessageSegment> data) {
        if (data == null || data.isEmpty()) return null;
        try {
            JsonNode resp = PostRequest.getPostResult(
                    RequestType.SEND_PRIVATE_MSG,
                    Map.of("user_id", userId, "message", data)
            );
            String messageId = resp.path("data").path("message_id").asText(null);
            if (messageId != null && !messageId.isEmpty()) return messageId;
            log.error("私聊消息发送失败，返回内容: {}", resp);
        } catch (Exception e) {
            log.error("私聊推送异常：{}", e.getMessage(), e);
        }
        return null;
    }

    public static String privateMessage(String userId, String text) {
        return privateMessage(userId, List.of(textSegment(text)));
    }

    public static String privateMessage(String userId, String imgData, ImageType type) {
        if (imgData == null || imgData.isBlank() || type == null) return null;
        return privateMessage(userId, List.of(imageSegment(imgData, type)));
    }

    public static String replyPrivateMessage(String userId, String messageId, String text) {
        return replyPrivateMessage(
                userId,
                messageId,
                List.of(textSegment(text))
        );
    }

    public static String replyPrivateMessage(String userId, String messageId, Collection<MessageSegment> messageSegments) {
        List<MessageSegment> replayContent = new ArrayList<>();
        replayContent.add(replySegment(messageId));
        if (messageSegments != null && !messageSegments.isEmpty()) {
            replayContent.addAll(messageSegments);
        }
        return privateMessage(userId, replayContent);
    }

    public static String chatMessage(String userId, String groupId, String text, boolean whetherAt) {
        if (!whetherAt) {
            return groupMessage(groupId, List.of(textSegment(text)));
        }
        return groupMessage(groupId, buildWithAtPrefix(userId, List.of(textSegment(text))));
    }

    public static String chatMessage(String userId, String groupId, Collection<MessageSegment> data, boolean whetherAt) {
        if (!whetherAt) return groupMessage(groupId, data);
        return groupMessage(groupId, buildWithAtPrefix(userId, data));
    }

    public static String chatMessage(String userId, String groupId, String imgData, ImageType type, boolean whetherAt) {
        if (!whetherAt) return sendSingleImageGroupMessage(groupId, imgData, type);
        if (imgData == null || imgData.isBlank() || type == null) return null;
        return groupMessage(groupId, buildWithAtPrefix(userId, List.of(imageSegment(imgData, type))));
    }

    public static String groupTextImageMessage(String groupId, String text, String imgData, ImageType type) {
        if ((text == null || text.isBlank()) && (imgData == null || imgData.isBlank())) return null;
        List<MessageSegment> segments = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            segments.add(textSegment(text));
        }
        if (imgData != null && !imgData.isBlank() && type != null) {
            segments.add(imageSegment(imgData, type));
        }
        return groupMessage(groupId, segments);
    }

    public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, String text) {
        return replyMessage(
                userId,
                groupId,
                messageId,
                whetherAt,
                List.of(textSegment(text))
        );
    }

    public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, Collection<MessageSegment> messageSegments) {
        List<MessageSegment> replayContent = whetherAt ? buildWithAtPrefix(userId, null) : new ArrayList<>();
        replayContent.add(replySegment(messageId));
        if (messageSegments != null && !messageSegments.isEmpty()) {
            replayContent.addAll(messageSegments);
        }
        return groupMessage(groupId, replayContent);
    }

    public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, String imgData, ImageType type) {
        if (imgData == null || imgData.isBlank() || type == null) return null;
        return replyMessage(userId, groupId, messageId, whetherAt, List.of(imageSegment(imgData, type)));
    }

    public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, String text, String imgData, ImageType type) {
        if (imgData == null || imgData.isBlank() || type == null) return null;
        List<MessageSegment> segments = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            segments.add(textSegment(text));
        }
        segments.add(imageSegment(imgData, type));
        return replyMessage(userId, groupId, messageId, whetherAt, segments);
    }

    public static String sendPrivateForwardMessage(String userId, Collection<MessageSegment> nodes, String title, String summary, String... textVars) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("user_id", userId);
            payload.put("messages", nodes);
            payload.put("news", buildNews(textVars));
            payload.put("source", title);
            payload.put("summary", summary);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_PRIVATE_FORWARD_MSG, payload);
            return resp.path("data").path("message_id").asText(null);
        } catch (Exception e) {
            log.warn("发送好友转发消息失败: {}", e.getMessage());
        }
        return null;
    }

    public static String sendGroupForwardMessage(String groupId, Collection<MessageSegment> nodes, String title, String summary, String... textVars) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("group_id", groupId);
            payload.put("messages", nodes);
            payload.put("news", buildNews(textVars));
            payload.put("source", title);
            payload.put("summary", summary);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_GROUP_FORWARD_MSG, payload);
            String forwardMessageId = resp.path("data").path("message_id").asText(null);
            if (forwardMessageId != null && !forwardMessageId.isEmpty()) {
                RM.recordLastMsg(groupId, forwardMessageId);
            }
            return forwardMessageId;
        } catch (Exception e) {
            log.warn("发送群转发消息失败: {}", e.getMessage());
        }
        return null;
    }

    public static String forwardSingleGroupMsg(String groupId, String messageId) {
        Map<String, Object> forwardMsg = new HashMap<>();
        forwardMsg.put("message_id", messageId);
        forwardMsg.put("group_id", groupId);
        JsonNode result = PostRequest.getPostResult(RequestType.FORWARD_SINGLE_MSG, forwardMsg);
        if (result != null) {
            String newMessageId = result.path("data").path("message_id").asText(null);
            if (newMessageId != null && !newMessageId.isEmpty()) {
                RM.recordLastMsg(groupId, newMessageId);
                return newMessageId;
            }
        }
        return messageId;
    }

    public static String sendSingleImageGroupMessage(String groupId, String imageData, ImageType type) {
        if (imageData == null || imageData.isBlank() || type == null) return null;
        return groupMessage(groupId, List.of(imageSegment(imageData, type)));
    }

    public static void atUser(String userId, String groupId, String text) {
        List<MessageSegment> replayContent = buildWithAtPrefix(userId, List.of(textSegment(text)));
        groupMessage(groupId, replayContent);
    }

    // ──────────────────────────────────
    // Node segment constructors (for forward messages)
    // ──────────────────────────────────

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

    public static MessageSegment createTextNodeSegment(MessageSegment rawMessage) {
        return createTextNodeSegment(rawMessage, FAKE_UIN, FAKE_NAME);
    }

    public static MessageSegment createTextNodeSegment(MessageSegment rawMessage, String uid, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("uin", uid);
        data.put("name", name);
        data.put("content", List.of(rawMessage));
        return new MessageSegment("node", data);
    }

    public static MessageSegment createTextNodeSegment(LinkedList<MessageSegment> arrayMessage) {
        return createTextNodeSegment(arrayMessage, FAKE_UIN, FAKE_NAME);
    }

    public static MessageSegment createTextNodeSegment(LinkedList<MessageSegment> arrayMessage, String uid, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("uin", uid);
        data.put("name", name);
        data.put("content", arrayMessage);
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

    // ──────────────────────────────────
    // Internal segment builders
    // ──────────────────────────────────

    private static MessageSegment textSegment(String text) {
        return new MessageSegment("text", Map.of("text", text));
    }

    private static MessageSegment atSegment(String userId) {
        return new MessageSegment("at", Map.of("qq", userId));
    }

    private static MessageSegment replySegment(String messageId) {
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

    private static List<MessageSegment> buildWithAtPrefix(String userId, Collection<MessageSegment> content) {
        List<MessageSegment> segments = new ArrayList<>();
        segments.add(atSegment(userId));
        segments.add(AT_GAP);
        if (content != null && !content.isEmpty()) {
            segments.addAll(content);
        }
        return segments;
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

    // ──────────────────────────────────
    // Request handling
    // ──────────────────────────────────

    public static void handleGroupRequest(boolean approve, String flag, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("flag", flag);
        payload.put("sub_type", "add");
        payload.put("approve", approve);
        if (reason != null) {
            payload.put("reason", reason);
        }
        PostRequest.sendPost(RequestType.HANDLE_GROUP_PENDING_REQUEST, payload);
        log.info("已{}群请求，flag: {}", approve ? "批准" : "拒绝", flag);
    }

    public static void recallMessage(String messageId) {
        PostRequest.sendSimplePost(RequestType.RECALL_MESSAGE, "message_id", messageId);
    }


    public static void setEmoji(String msgId, int emojiId, boolean set) {
        try {
            Map<String, Object> req = new HashMap<>(4);
            req.put("message_id", msgId);
            req.put("emoji_id", emojiId);
            req.put("set", set);
            PostRequest.sendPost(RequestType.PUT_EMOJI, req);
        } catch (Exception e) {
            log.warn("Emoji API fail: {}", e.getMessage());
        }
    }

    public enum ImageType {
        URL, BASE64, FILE
    }
}
