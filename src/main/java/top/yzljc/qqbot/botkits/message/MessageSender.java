package top.yzljc.qqbot.botkits.message;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.debug.RecallLastMsg;

import java.util.*;

public class MessageSender {
    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    // 发送纯文本群消息
    public static void sendGroupMessage(long groupId, String content) {
        sendGroupMessage(groupId, content, null,true);
    }

    // 发送带图片的群消息
    public static void sendGroupMessage(long groupId, String text, String imageData) {
        sendGroupMessage(groupId, text, imageData, true);
    }

    // 发送带http连接请求类型的图片的群消息
    public static void sendGroupMessage(long groupId, String text, String imageData, boolean isBase64) {
        ThreadManager.execute(() -> {
            Long messageId = handleGroupMsg(groupId, text, imageData, isBase64);
            if (messageId != null) {
                log.info("消息发送成功{} -> 群: {}", (imageData != null ? " [含图片]" : ""), groupId);
            }
        });
    }

    // 发送纯文本群消息并返回消息ID，如果之后我换了方法的话就给base64填上去再写一个新的函数
    public static Long sendGroupMessageGetId(long groupId, String content) {
        return handleGroupMsg(groupId, content, null,true);
    }

    // 发送私聊消息
    public static void sendPrivateMessage(long userId, String content) {
        handlePrivateMsg(userId, content);
    }

    // 发送群聊聊天 - 数据格式
    @SuppressWarnings("UnusedReturnValue")
    public static Long sendGroupData(long groupId, List<Map<String, Object>> msgData) {
        return handleGroupData(groupId, msgData);
    }

    // 私聊消息的上报实现，如果需要扩展获取message_id则将函数改为Long类型返回
    private static void handlePrivateMsg(long userId, String text) {
        try {
            List<Map<String, Object>> messageNodes = getMaps(text, null, true); // imgData没写，直接null吧用到再说
            if (messageNodes.isEmpty()) return;

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("user_id", userId);
            payloadMap.put("message", messageNodes);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_PRIVATE_MSG, payloadMap);

            if (resp != null && resp.has("data") && resp.get("data").has("message_id")) {
                resp.get("data").get("message_id").asLong();
            } else {
                log.error("私聊消息发送失败，返回内容: {}", resp);
            }
        } catch (Exception ex) {
            log.error("推送异常：{}", ex.getMessage(), ex);
        }
    }

    private static Long handleGroupMsg(long groupId, String text, String imageData, boolean isBase64) {
        try {
            List<Map<String, Object>> messageNodes = getMaps(text, imageData, isBase64);
            if (messageNodes.isEmpty()) return null;

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", messageNodes);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_GROUP_MSG, payloadMap);

            if (resp != null && resp.has("data") && resp.get("data").has("message_id")) {
                long messageId = resp.get("data").get("message_id").asLong();
                RecallLastMsg.recordLastMsg(groupId, messageId);
                return messageId;
            } else {
                log.error("消息发送失败，返回内容: {}", resp);
            }
        } catch (Exception ex) {
            log.error("推送异常：{}", ex.getMessage(), ex);
        }
        return null;
    }

    private static Long handleGroupData(long groupId, List<Map<String, Object>> msgData) {
        try {
            if (msgData.isEmpty()) return null;

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", msgData);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_GROUP_MSG, payloadMap);

            if (resp != null && resp.has("data") && resp.get("data").has("message_id")) {
                long messageId = resp.get("data").get("message_id").asLong();
                RecallLastMsg.recordLastMsg(groupId, messageId);
                return messageId;
            } else {
                log.error("消息发送失败，返回内容: {}", resp);
            }
        } catch (Exception ex) {
            log.error("推送异常：{}", ex.getMessage(), ex);
        }
        return null;
    }

    private static List<Map<String, Object>> getMaps(String text, String imageData, boolean isBase64) {
        List<Map<String, Object>> messageNodes = new ArrayList<>();

        if (text != null && !text.isEmpty()) {
            Map<String, Object> textData = new HashMap<>();
            textData.put("text", text);
            Map<String, Object> textNode = new HashMap<>();
            textNode.put("type", "text");
            textNode.put("data", textData);
            messageNodes.add(textNode);
        }

        if (imageData != null && !imageData.isEmpty()) {
            Map<String, Object> imgData = new HashMap<>();
            if (isBase64){
                imgData.put("file", "base64://" + imageData);
            }else{
                imgData.put("file", imageData);
            }

            Map<String, Object> imgNode = new HashMap<>();
            imgNode.put("type", "image");
            imgNode.put("data", imgData);
            messageNodes.add(imgNode);
        }
        return messageNodes;
    }
}