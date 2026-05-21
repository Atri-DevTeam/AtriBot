package top.yzljc.qqbot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficePrivateChatEvent
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
@AllArgsConstructor
public class OfficialPrivateChatEvent extends Event {
    private final String msgId;
    private final String content;
    private final String timestamp;
    private final String openId;
    private final Object attachments;

    @SuppressWarnings("UnusedReturnValue")
    public String replyText(String text) {
        return AtriBot.getInstance().getMessageService().replyPrivateTextMessage(this.openId, this.msgId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdown) {
        return AtriBot.getInstance().getMessageService().replyPrivateMarkdownMessage(this.openId, this.msgId, markdown);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdown, Object keyboard) {
        return AtriBot.getInstance().getMessageService().replyPrivateMarkdownWithKeyboard(this.openId, this.msgId, markdown, keyboard);
    }
}