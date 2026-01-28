package top.yzljc.qqbot.botkits.message;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;

import java.util.*;
import java.util.concurrent.Executors;

public class MessageSender {
    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    /**
     * 发送纯文本群消息
     */
    public static void sendGroupMessage(long groupId, String content) {
        sendGroupMessage(groupId, content, null);
    }

    /**
     * 发送带图片的群消息
     */
    public static void sendGroupMessage(long groupId, String text, String base64Image) {
        Executors.newSingleThreadExecutor().submit(() -> {
            Long messageId = executeRequest(groupId, text, base64Image);
            if (messageId != null) {
                log.info("消息发送成功{} -> 群: {}", (base64Image != null ? " [含图片]" : ""), groupId);
            }
        });
    }

    /**
     * 发送纯文本群消息并返回消息ID，如果之后我换了方法的话就给base64填上去再写一个新的函数
     */
    public static Long sendGroupMessageGetId(long groupId, String content) {
        return executeRequest(groupId, content, null);
    }

    private static Long executeRequest(long groupId, String text, String base64Image) {
        try {
            List<Map<String, Object>> messageNodes = getMaps(text, base64Image);
            if (messageNodes.isEmpty()) return null;

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", messageNodes);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_GROUP_MSG, payloadMap);

            if (resp != null && resp.has("data") && resp.get("data").has("message_id")) {
                return resp.get("data").get("message_id").asLong();
            } else {
                log.error("消息发送失败，返回内容: {}", resp);
            }
        } catch (Exception ex) {
            log.error("推送异常：{}", ex.getMessage(), ex);
        }
        return null;
    }

    private static List<Map<String, Object>> getMaps(String text, String base64Image) {
        List<Map<String, Object>> messageNodes = new ArrayList<>();

        if (text != null && !text.isEmpty()) {
            Map<String, Object> textData = new HashMap<>();
            textData.put("text", text);
            Map<String, Object> textNode = new HashMap<>();
            textNode.put("type", "text");
            textNode.put("data", textData);
            messageNodes.add(textNode);
        }

        if (base64Image != null && !base64Image.isEmpty()) {
            Map<String, Object> imgData = new HashMap<>();
            imgData.put("file", "base64://" + base64Image);
            Map<String, Object> imgNode = new HashMap<>();
            imgNode.put("type", "image");
            imgNode.put("data", imgData);
            messageNodes.add(imgNode);
        }
        return messageNodes;
    }
}