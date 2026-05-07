package top.yzljc.qqbot.official;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.yzljc.qqbot.official.service.QQBotMessageService;

/**
 * @Author YZ_Ljc_
 * @ClassName Chat
 * @Created_at 2026/05/07
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official
 */
@Component
public class Chat {

    @Autowired
    private QQBotMessageService service;

    public void replyPrivateTextMessage(String userOpenId, String messageOpenId, String content) {
        service.replyPrivateTextMessage(userOpenId, messageOpenId, content);
    }

    public void replyGroupTextMessage(String groupOpenId, String messageOpenId, String content) {
        service.replyGroupTextMessage(groupOpenId, messageOpenId, content);
    }

    public void replyGroupMarkdownMessage(String groupOpenId, String messageOpenId, String content) {
        service.replyGroupMarkdownMessage(groupOpenId, messageOpenId, content);
    }

    public void replyPrivateMarkdownMessage(String userOpenId, String messageOpenId, String content) {
        service.replyPrivateMarkdownMessage(userOpenId, messageOpenId, content);
    }
}