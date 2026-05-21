package top.yzljc.qqbot.official.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.official.AtText;
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
            return msgSeqCache.get(msgId, () -> new AtomicInteger(0)).incrementAndGet();
        } catch (Exception e) {
            log.error("获取 msg_seq 异常, msgId: {}", msgId, e);
            return 1;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendPrivateMessage(String openId, MessageBody request) {
        String url = apiBaseUrl + "/v2/users/" + openId + "/messages";
        return doSendMessage(url, request, "单聊");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendGroupMessage(String groupOpenId, MessageBody request) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages";
        return doSendMessage(url, request, "群聊");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyGroupTextMessage(String groupOpenId, String msgId, String replyText) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .content(replyText)
                .build();

        return sendGroupMessage(groupOpenId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyGroupMarkdownMessage(String groupOpenId, String userOpenId, String msgId, String markdownContent) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", AtText.at(userOpenId) + markdownContent);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(markdownObj)
                .build();

        return sendGroupMessage(groupOpenId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyPrivateTextMessage(String openId, String msgId, String replyText) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .content(replyText)
                .build();

        return sendPrivateMessage(openId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyPrivateMarkdownMessage(String openId, String msgId, String markdownContent) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", markdownContent);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(markdownObj)
                .build();

        return sendPrivateMessage(openId, request);
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

    @SuppressWarnings("UnusedReturnValue")
    public String replyPrivateImageMessage(String openId, String msgId, String base64Content) {
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
                return null;
            }

            JsonNode resNode = objectMapper.readTree(uploadRes);
            if (!resNode.has("file_info")) {
                log.error("单聊-图片上传失败，未返回 file_info: {}", uploadRes);
                return null;
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

            return sendPrivateMessage(openId, request);

        } catch (Exception e) {
            log.error("发送单聊图片异常: ", e);
            return null;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyPrivateMarkdownWithKeyboard(String openId, String msgId, String markdownContent, Object keyboard) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", markdownContent);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(markdownObj)
                .keyboard(keyboard)
                .build();

        return sendPrivateMessage(openId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyGroupMarkdownWithKeyboard(String groupOpenId, String userOpenId, String msgId, String markdownContent, Object keyboard) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", AtText.at(userOpenId) + markdownContent);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(markdownObj)
                .keyboard(keyboard)
                .build();

        return sendGroupMessage(groupOpenId, request);
    }

    public void recall(String label, String openId, String messageOpenId) {
        if (label.equals("1")) recallPrivateMessage(openId, messageOpenId);
        if (label.equals("2")) recallGroupMessage(openId, messageOpenId);
    }

    public void recallPrivateMessage(String userOpenId, String messageId) {
        String url = apiBaseUrl + "/v2/users/" + userOpenId + "/messages/" + messageId;
        try {
            String result = HttpService.deleteRequestStr(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
            log.info("撤回单聊消息成功, userOpenId: {}, messageId: {}, result: {}", userOpenId, messageId, result);
        } catch (Exception e) {
            log.error("撤回单聊消息失败, userOpenId: {}, messageId: {}", userOpenId, messageId, e);
        }
    }

    public void recallGroupMessage(String groupOpenId, String messageId) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages/" + messageId;
        try {
            String result = HttpService.deleteRequestStr(url, "Authorization", "QQBot " + tokenManager.getAccessToken());
            log.info("撤回群聊消息成功, groupOpenId: {}, messageId: {}, result: {}", groupOpenId, messageId, result);
        } catch (Exception e) {
            log.error("撤回群聊消息失败, groupOpenId: {}, messageId: {}", groupOpenId, messageId, e);
        }
    }

    private String doSendMessage(String url, MessageBody request, String logType) {
        try {
            String json = objectMapper.writeValueAsString(request);
            JsonNode result = HttpService.postJson(url, json,
                    "Authorization", "QQBot " + tokenManager.getAccessToken());
            if (result != null) {
                return result.get("id").asText() != null ? result.get("id").asText() : null;
            }
            return null;
        } catch (JsonProcessingException e) {
            log.error("{}消息序列化失败: ", logType, e);
            return null;
        }
    }
}