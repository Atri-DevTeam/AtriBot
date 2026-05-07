package top.yzljc.qqbot.official.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import top.yzljc.qqbot.official.MessageBody;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class QQBotMessageService {

    @Value("${qqbot.api-base-url}")
    private String apiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private QQBotTokenManager tokenManager;

    public void sendGroupMessage(String groupOpenId, MessageBody request) {
        String url = apiBaseUrl + "/v2/groups/" + groupOpenId + "/messages";
        doSendMessage(url, request, "群聊");
    }

    public void replyGroupTextMessage(String groupOpenId, String msgId, String replyText) {
        MessageBody request = MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .msgId(msgId)
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
                .markdown(markdownObj)
                .build();

        sendPrivateMessage(openId, request);
    }

    private void doSendMessage(String url, MessageBody request, String logType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "QQBot " + tokenManager.getAccessToken());

        HttpEntity<MessageBody> entity = new HttpEntity<>(request, headers);

        try {
            String response = restTemplate.postForObject(url, entity, String.class);
            log.info("{}消息发送成功: {}", logType, response);
        } catch (Exception e) {
            log.error("{}消息发送失败: ", logType, e);
        }
    }
}