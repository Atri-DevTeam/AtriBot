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
    private final boolean bot;
    // 此字段意义不明，但官方接口中确实存在，暂时保留
    private final String userOpenId;
    private final String username;
    // 此字段意义不明，但官方接口中确实存在，暂时保留
    private final String id;
    private final String msgId;
    private final String content;
    private final String timestamp;
    // 统一使用 unionOpenId
    private final String unionOpenId;
    private final Object attachments;

    @SuppressWarnings("UnusedReturnValue")
    public String replyText(String text) {
        return AtriBot.getInstance().getMessageService().replyPrivateTextMessage(this.unionOpenId, this.msgId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdown) {
        return AtriBot.getInstance().getMessageService().replyPrivateMarkdownMessage(this.unionOpenId, this.msgId, markdown);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdown, Object keyboard) {
        return AtriBot.getInstance().getMessageService().replyPrivateMarkdownWithKeyboard(this.unionOpenId, this.msgId, markdown, keyboard);
    }
}