package top.yzljc.qqbot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupCommandEvent
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
@AllArgsConstructor
public class OfficialGroupChatEvent extends Event {
    private final String msgId;
    private final String groupOpenId;
    private final String content;
    private final String timestamp;
    private final String memberOpenId;
    private final Object attachments;

    public void replyText(String text) {
        AtriBot.getInstance().getMessageService().replyGroupTextMessage(this.groupOpenId, this.msgId, text);
    }

    public void replyMarkdown(String markdownContent) {
        AtriBot.getInstance().getMessageService().replyGroupMarkdownMessage(this.groupOpenId, this.msgId, markdownContent);
    }

    public void replyMarkdown(String markdownContent, Object keyboard) {
        AtriBot.getInstance().getMessageService().replyGroupMarkdownWithKeyboard(this.groupOpenId, this.msgId, markdownContent, keyboard);
    }
}