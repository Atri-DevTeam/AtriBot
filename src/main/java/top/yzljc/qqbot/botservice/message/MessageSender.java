package top.yzljc.qqbot.botservice.message;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botservice.request.RequestType;
import top.yzljc.qqbot.botservice.request.PostRequest;
import top.yzljc.qqbot.botservice.thread.ThreadManager;
import top.yzljc.qqbot.botservice.tools.RM;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MessageSender {
    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);
    public static class MessageResult {
        private final CompletableFuture<Long> future;

        public MessageResult(CompletableFuture<Long> future) {
            this.future = future;
        }

        public Long getMessageId() {
            try {
                Long id = future.join();
                return id != null ? id : 0L;
            } catch (Exception e) {
                return 0L;
            }
        }

        public boolean isSuccess() {
            return getMessageId() != 0L;
        }

        public void then(Consumer<Long> action) {
            future.thenAccept(id -> {
                if (id != null && id != 0L) {
                    action.accept(id);
                }
            });
        }
    }

    // 发送纯文本群消息
    public static MessageResult sendGroupMessage(long groupId, String content) {
        return sendGroupMessage(groupId, content, null, true);
    }

    // 发送带图片的群消息
    public static MessageResult sendGroupMessage(long groupId, String text, String imageData) {
        return sendGroupMessage(groupId, text, imageData, true);
    }

    // 发送带http连接请求类型的图片的群消息
    public static MessageResult sendGroupMessage(long groupId, String text, String imageData, boolean isBase64) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        ThreadManager.execute(() -> {
            Long messageId = handleGroupMsg(groupId, text, imageData, isBase64);
            if (messageId != 0L) {
                log.info("消息发送成功{} -> 群: {}", (imageData != null ? " [含图片]" : ""), groupId);
            }
            future.complete(messageId);
        });
        return new MessageResult(future);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static MessageResult sendPrivateMessage(long userId, String content) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        ThreadManager.execute(() -> {
            future.complete(handlePrivateMsg(userId, content));
        });
        return new MessageResult(future);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static MessageResult sendGroupData(long groupId, List<Map<String, Object>> msgData) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        ThreadManager.execute(() -> {
            future.complete(handleGroupData(groupId, msgData));
        });
        return new MessageResult(future);
    }

    private static Long handlePrivateMsg(long userId, String text) {
        try {
            List<Map<String, Object>> messageNodes = getMaps(text, null, true);
            if (messageNodes.isEmpty()) return 0L;

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("user_id", userId);
            payloadMap.put("message", messageNodes);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_PRIVATE_MSG, payloadMap);

            if (resp != null && resp.has("data") && resp.get("data").has("message_id")) {
                return resp.get("data").get("message_id").asLong();
            } else {
                log.error("私聊消息发送失败，返回内容: {}", resp);
            }
        } catch (Exception ex) {
            log.error("推送异常：{}", ex.getMessage(), ex);
        }
        return 0L;
    }

    private static Long handleGroupMsg(long groupId, String text, String imageData, boolean isBase64) {
        try {
            List<Map<String, Object>> messageNodes = getMaps(text, imageData, isBase64);
            if (messageNodes.isEmpty()) return 0L;

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", messageNodes);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_GROUP_MSG, payloadMap);

            if (resp != null && resp.has("data") && resp.get("data").has("message_id")) {
                long messageId = resp.get("data").get("message_id").asLong();
                RM.recordLastMsg(groupId, messageId);
                return messageId;
            } else {
                log.error("消息发送失败，返回内容: {}", resp);
            }
        } catch (Exception ex) {
            log.error("推送异常：{}", ex.getMessage(), ex);
        }
        return 0L;
    }

    private static Long handleGroupData(long groupId, List<Map<String, Object>> msgData) {
        try {
            if (msgData.isEmpty()) return 0L;

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", msgData);

            JsonNode resp = PostRequest.getPostResult(RequestType.SEND_GROUP_MSG, payloadMap);

            if (resp != null && resp.has("data") && resp.get("data").has("message_id")) {
                long messageId = resp.get("data").get("message_id").asLong();
                RM.recordLastMsg(groupId, messageId);
                return messageId;
            } else {
                log.error("消息发送失败，返回内容: {}", resp);
            }
        } catch (Exception ex) {
            log.error("推送异常：{}", ex.getMessage(), ex);
        }
        return 0L;
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