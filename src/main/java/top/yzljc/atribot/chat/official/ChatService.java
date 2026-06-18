package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.PermissionType;
import top.yzljc.atribot.chat.official.media.GroupMessageType;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.OfficialActiveMessageFailEvent;
import top.yzljc.atribot.function.official.ChatContentRecord;
import top.yzljc.atribot.platform.official.TokenManager;
import top.yzljc.atribot.service.request.HttpService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ChatService {

    private final String apiBaseUrl;
    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, AtomicInteger> msgSeqCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

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

    @SuppressWarnings("UnusedReturnValue")
    public String sendPrivateMessage(String openId, MessageBody request) {
        String url = apiBaseUrl + "/v2/users/" + openId + "/messages";
        String messageId = doSendMessage(url, request, "单聊");
        if (messageId != null) {
            ChatContentRecord.recordSentC2CMessage(openId, request, messageId);
        }
        return messageId;
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendGroupMessage(String groupOpenId, MessageBody request) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages";
        String messageId = doSendMessage(url, request, "群聊");
        if (messageId != null) {
            ChatContentRecord.recordSentGroupMessage(groupOpenId, request, messageId);
        }
        return messageId;
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendActiveGroupTextMessage(String groupOpenId, String text) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(text)
                .build();

        return sendGroupMessage(groupOpenId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendActivePrivateTextMessage(String openId, String text) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(text)
                .build();

        return sendPrivateMessage(openId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendActiveGroupMarkdownMessage(String groupOpenId, Markdown markdown) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .markdown(buildMarkdown(markdown))
                .build();

        return sendGroupMessage(groupOpenId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendActiveGroupMarkdownMessage(String groupOpenId, Markdown markdown, Object keyboard) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .markdown(buildMarkdown(markdown))
                .keyboard(keyboard)
                .build();

        return sendGroupMessage(groupOpenId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendActivePrivateMarkdownMessage(String openId, Markdown markdown) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .markdown(buildMarkdown(markdown))
                .build();

        return sendPrivateMessage(openId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendActivePrivateMarkdownMessage(String openId, Markdown markdown, Object keyboard) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .markdown(buildMarkdown(markdown))
                .keyboard(keyboard)
                .build();

        return sendPrivateMessage(openId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendActivePrivateImageMessage(String openId, ImageType type, String value) {
        String uploadUrl = apiBaseUrl + "/v2/users/" + openId + "/files";
        String fileInfo = uploadImageFile(uploadUrl, type, value, "单聊主动");
        if (fileInfo == null) return null;

        Map<String, Object> mediaObj = new HashMap<>();
        mediaObj.put("file_info", fileInfo);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MEDIA.getValue())
                .media(mediaObj)
                .build();

        return sendPrivateMessage(openId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendActiveGroupImageMessage(String groupOpenId, ImageType type, String value) {
        String uploadUrl = apiBaseUrl + "/v2/groups/" + groupOpenId + "/files";
        String fileInfo = uploadImageFile(uploadUrl, type, value, "群聊主动");
        if (fileInfo == null) return null;

        Map<String, Object> mediaObj = new HashMap<>();
        mediaObj.put("file_info", fileInfo);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MEDIA.getValue())
                .media(mediaObj)
                .build();

        return sendGroupMessage(groupOpenId, request);
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
    public String replyGroupMarkdownMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(buildMarkdown(Markdown.at(userOpenId) + "\n" + markdown.getText() + "\n\n" + "[☁ 聊天群](https://qm.qq.com/q/s9ZPDwAm0C)"))
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
    public String replyPrivateMarkdownMessage(String openId, String msgId, Markdown markdown) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(buildMarkdown(markdown))
                .build();

        return sendPrivateMessage(openId, request);
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

    @SuppressWarnings("UnusedReturnValue")
    public String replyPrivateImageMessage(String openId, String msgId, ImageType type, String value) {
        String uploadUrl = apiBaseUrl + "/v2/users/" + openId + "/files";
        String fileInfo = uploadImageFile(uploadUrl, type, value, "单聊");
        if (fileInfo == null) return null;

        Map<String, Object> mediaObj = new HashMap<>();
        mediaObj.put("file_info", fileInfo);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MEDIA.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .media(mediaObj)
                .build();

        return sendPrivateMessage(openId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyGroupImageMessage(String groupOpenId, String msgId, ImageType type, String value) {
        String uploadUrl = apiBaseUrl + "/v2/groups/" + groupOpenId + "/files";
        String fileInfo = uploadImageFile(uploadUrl, type, value, "群聊");
        if (fileInfo == null) return null;

        Map<String, Object> mediaObj = new HashMap<>();
        mediaObj.put("file_info", fileInfo);

        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MEDIA.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .media(mediaObj)
                .build();

        return sendGroupMessage(groupOpenId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyPrivateMarkdownMessage(String openId, String msgId, Markdown markdown, Object keyboard) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(buildMarkdown(markdown))
                .keyboard(keyboard)
                .build();

        return sendPrivateMessage(openId, request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyGroupMarkdownMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown, Object keyboard) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .msgId(msgId)
                .msgSeq(getNextMsgSeq(msgId))
                .markdown(buildMarkdown(Markdown.at(userOpenId) + "\n" + markdown.getText() + "\n\n" + "[☁ 聊天群](https://qm.qq.com/q/s9ZPDwAm0C)"))
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

        try {
            String uploadJson = objectMapper.writeValueAsString(uploadData);
            String uploadRes = HttpService.postJsonForString(uploadUrl, uploadJson,
                    "Authorization", "QQBot " + tokenManager.getAccessToken());

            if (uploadRes == null || uploadRes.isBlank()) {
                log.error("{}-图片上传失败，服务器返回为空", logLabel);
                return null;
            }

            JsonNode resNode = objectMapper.readTree(uploadRes);
            if (!resNode.has("file_info")) {
                log.error("{}-图片上传失败，未返回 file_info: {}", logLabel, uploadRes);
                return null;
            }
            return resNode.get("file_info").asText();
        } catch (Exception e) {
            log.error("发送{}图片异常: ", logLabel, e);
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

    private String doSendMessage(String url, MessageBody request, String logType) {
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