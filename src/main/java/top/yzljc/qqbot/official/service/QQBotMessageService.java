package top.yzljc.qqbot.official.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.official.MessageBody;
import top.yzljc.qqbot.service.request.HttpService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class QQBotMessageService {

    private final String apiBaseUrl;
    private final QQBotTokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 使用 Guava Cache，写入后 5 分钟自动过期清理
    private final Cache<String, AtomicInteger> msgSeqCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public QQBotMessageService(String apiBaseUrl, QQBotTokenManager tokenManager) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
    }

    private int getNextMsgSeq(String msgId) {
        if (msgId == null) return 1;
        try {
            // 如果缓存中没有该 msgId，则初始化为 0，然后 incrementAndGet 变成 1
            return msgSeqCache.get(msgId, () -> new AtomicInteger(0)).incrementAndGet();
        } catch (Exception e) {
            log.error("获取 msg_seq 异常, msgId: {}", msgId, e);
            return 1;
        }
    }

    public void sendGroupMessage(String groupOpenId, MessageBody request) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages";
        doSendMessage(url, request, "群聊");
    }

    public void replyGroupTextMessage(String groupOpenId, String msgId, String replyText) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .content(replyText)
                .build();

        sendGroupMessage(groupOpenId, request);
    }

    public void replyGroupMarkdownMessage(String groupOpenId, String msgId, String markdownContent) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", markdownContent);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(markdownObj)
                .build();

        sendGroupMessage(groupOpenId, request);
    }

    public void sendPrivateMessage(String openId, MessageBody request) {
        String url = apiBaseUrl + "/v2/users/" + openId + "/messages";
        doSendMessage(url, request, "单聊");
    }

    public void replyPrivateTextMessage(String openId, String msgId, String replyText) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .content(replyText)
                .build();

        sendPrivateMessage(openId, request);
    }

    public void replyPrivateMarkdownMessage(String openId, String msgId, String markdownContent) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", markdownContent);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(markdownObj)
                .build();

        sendPrivateMessage(openId, request);
    }

    public Object buildCmdKeyboard(List<List<CommandButton>> layout) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (List<CommandButton> rowBtns : layout) {
            List<Map<String, Object>> buttons = new ArrayList<>();

            for (CommandButton btn : rowBtns) {
                Map<String, Object> action = new HashMap<>();
                action.put("type", btn.getType());
                action.put("data", btn.getCommand());
                action.put("enter", btn.isEnter());
                action.put("unsupport_tips", "当前客户端版本不支持此按钮");

                Map<String, Object> permission = new HashMap<>();
                permission.put("type", 2);
                action.put("permission", permission);

                Map<String, Object> renderData = new HashMap<>();
                renderData.put("label", btn.getLabel());
                renderData.put("visited_label", btn.getLabel());
                renderData.put("style", btn.getStyle());

                Map<String, Object> button = new HashMap<>();
                button.put("id", btn.getId());
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

    public void replyPrivateImageMessage(String openId, String msgId, String base64Content) {
        String uploadUrl = apiBaseUrl + "/v2/users/" + openId + "/files";

        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("file_type", 1);
        uploadData.put("file_data", base64Content);
        uploadData.put("srv_send_msg", false);

        try {
            String uploadJson = objectMapper.writeValueAsString(uploadData);
            String uploadRes = HttpService.postJsonForString(uploadUrl, uploadJson,
                    "Authorization", "QQBot " + tokenManager.getAccessToken());

            if (uploadRes == null || uploadRes.isBlank()) {
                log.error("单聊-图片上传失败，服务器返回为空");
                return;
            }

            JsonNode resNode = objectMapper.readTree(uploadRes);
            if (!resNode.has("file_info")) {
                log.error("单聊-图片上传失败，未返回 file_info: {}", uploadRes);
                return;
            }
            String fileInfo = resNode.get("file_info").asText();

            Map<String, Object> mediaObj = new HashMap<>();
            mediaObj.put("file_info", fileInfo);

            MessageBody request = MessageBody.builder()
                    .msgType(GroupMessageType.MEDIA.getValue())
                    .msgId(msgId)
                    .msgSeq(getNextMsgSeq(msgId))
                    .media(mediaObj)
                    .build();

            sendPrivateMessage(openId, request);

        } catch (Exception e) {
            log.error("发送单聊图片异常: ", e);
        }
    }

    public void replyPrivateMarkdownWithKeyboard(String openId, String msgId, String markdownContent, Object keyboard) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", markdownContent);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(markdownObj)
                .keyboard(keyboard)
                .build();

        sendPrivateMessage(openId, request);
    }

    public void replyGroupMarkdownWithKeyboard(String groupId, String msgId, String markdownContent, Object keyboard) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", markdownContent);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(markdownObj)
                .keyboard(keyboard)
                .build();

        sendGroupMessage(groupId, request);
    }

    private void doSendMessage(String url, MessageBody request, String logType) {
        try {
            String json = objectMapper.writeValueAsString(request);
            String result = HttpService.postJsonForString(url, json,
                    "Authorization", "QQBot " + tokenManager.getAccessToken());
            log.info("{}消息发送成功: {}", logType, result);
        } catch (JsonProcessingException e) {
            log.error("{}消息序列化失败: ", logType, e);
        }
    }
}