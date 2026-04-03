package top.yzljc.qqbot.botservice.message;

import top.yzljc.qqbot.botservice.request.PostRequest;
import top.yzljc.qqbot.botservice.request.RequestType;
import top.yzljc.qqbot.utils.Logger;

import java.util.*;

public class MessageUtils {
    private static final String FAKE_UIN = "3199590352";
    private static final String FAKE_NAME = "YZ_Ljc_";

    public static void replyMessage(long userId, long groupId,long messageId,String text){
        replyMessage(userId,groupId,messageId,false,text);
    }

    public static void replyMessage(long userId, long groupId, long messageId, boolean whetherAt, String text) {
        List<Map<String, Object>> replayContent = new ArrayList<>();

        if (whetherAt) {
            Map<String, Object> replyAt = new HashMap<>();
            replyAt.put("qq", userId);

            Map<String, Object> atNode = new HashMap<>();
            atNode.put("type", "at");
            atNode.put("data", replyAt);
            replayContent.add(atNode);
        }

        Map<String, Object> replyMsg = new HashMap<>();
        replyMsg.put("id", messageId);

        Map<String, Object> replyNode = new HashMap<>();
        replyNode.put("type", "reply");
        replyNode.put("data", replyMsg);
        replayContent.add(replyNode);

        Map<String, Object> replyText = new HashMap<>();
        replyText.put("text", text);

        Map<String, Object> textNode = new HashMap<>();
        textNode.put("type", "text");
        textNode.put("data", replyText);
        replayContent.add(textNode);

        MessageSender.sendGroupData(groupId, replayContent);
    }

    public static void sendPrivateForwardMessage(long userId, List<Map<String, Object>> nodes, String title, String summary, String... textVars) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("user_id", userId);
            payload.put("messages", nodes);

            List<Map<String, String>> news = new ArrayList<>();
            if (textVars != null) {
                for (String textVar : textVars) {
                    if (textVar != null && !textVar.isEmpty()) {
                        news.add(Map.of("text", textVar));
                    }
                }
            }
            payload.put("news", news);

            payload.put("source", title);
            payload.put("summary", summary);

            PostRequest.sendPost(RequestType.SEND_PRIVATE_FORWARD_MSG, payload);

        } catch (Exception e) {
            Logger.warn("发送好友转发消息失败: {}", e.getMessage());
        }
    }

    public static void sendGroupForwardMessage(long groupId, List<Map<String, Object>> nodes, String title, String summary, String... textVars) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("group_id", groupId);
            payload.put("messages", nodes);

            List<Map<String, String>> news = new ArrayList<>();
            if (textVars != null) {
                for (String textVar : textVars) {
                    if (textVar != null && !textVar.isEmpty()) {
                        news.add(Map.of("text", textVar));
                    }
                }
            }
            payload.put("news", news);

            payload.put("source", title);
            payload.put("summary", summary);

            PostRequest.sendPost(RequestType.SEND_GROUP_FORWARD_MSG, payload);

        } catch (Exception e) {
            Logger.warn("发送群转发消息失败: {}", e.getMessage());
        }
    }

    public static void forwardSingleGroupMsg(long groupId, long messageId){
        Map<String, Object> forwardMsg = new HashMap<>();
        forwardMsg.put("message_id", messageId);
        forwardMsg.put("group_id", groupId);
        PostRequest.sendPost(RequestType.FORWARD_SINGLE_MSG, forwardMsg);
    }

    public static void sendSingleImageGroupMsg(long groupId, ImageType type, String imageData){
        Map<String, Object> singleImageData = new HashMap<>();
        singleImageData.put("type", "image");
        Map<String, Object> data = new HashMap<>();
        switch (type){
            case FILE -> data.put("file", "file://" + imageData);
            case URL -> data.put("url", imageData);
            case BASE64 -> data.put("file", "base64://" + imageData);
        }
        singleImageData.put("data", data);
        Map<String, Object> payload = new HashMap<>();
        payload.put("group_id", groupId);
        payload.put("message", singleImageData);
        PostRequest.sendPost(RequestType.SEND_GROUP_MSG, payload);
    }

    public static void atUser(long userId, long groupId, String text) {
        List<Map<String, Object>> replayContent = new ArrayList<>();

        Map<String, Object> replyAt = new HashMap<>();
        replyAt.put("qq", userId);

        Map<String, Object> atNode = new HashMap<>();
        atNode.put("type", "at");
        atNode.put("data", replyAt);
        replayContent.add(atNode);

        Map<String, Object> replyText = new HashMap<>();
        replyText.put("text", text);

        Map<String, Object> textNode = new HashMap<>();
        textNode.put("type", "text");
        textNode.put("data", replyText);
        replayContent.add(textNode);

        MessageSender.sendGroupData(groupId, replayContent);
    }

    public static Map<String, Object> createTextNode(String text) {
        return createTextNode(text, FAKE_UIN, FAKE_NAME);
    }

    public static Map<String, Object> createTextNode(String text,String uin, String name) {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "node");
        Map<String, Object> data = new HashMap<>();
        data.put("uin", uin);
        data.put("name", name);

        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textItem = new HashMap<>();
        textItem.put("type", "text");
        Map<String, Object> textData = new HashMap<>();
        textData.put("text", text);
        textItem.put("data", textData);
        contentList.add(textItem);

        data.put("content", contentList);
        msgData.put("data", data);
        return msgData;
    }

    public static Map<String, Object> createImageNode(String url) {
        return createImageNode(url, FAKE_UIN, FAKE_NAME);
    }

    public static Map<String, Object> createImageNode(String url, String uin, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "node");
        Map<String, Object> data = new HashMap<>();
        data.put("uin", uin);
        data.put("name", name);

        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> imgItem = new HashMap<>();
        imgItem.put("type", "image");
        Map<String, Object> imgData = new HashMap<>();
        imgData.put("file", url);
        imgItem.put("data", imgData);
        contentList.add(imgItem);

        data.put("content", contentList);
        node.put("data", data);
        return node;
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
