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
public class OfficialGroupAtMessageCreateEvent extends Event {
    private final boolean bot;
    // 此字段意义不明，但官方接口中确实存在，暂时保留
    private final String id;
    private final String msgId;
    // 此字段意义不明，但官方接口中确实存在，暂时保留
    private final String groupId;
    private final String groupOpenId;
    private final String content;
    private final String timestamp;
    private final String username;
    // 此字段意义不明，但官方接口中确实存在，暂时保留
    private final String memberOpenId;
    // 统一使用 unionOpenId
    private final String unionOpenId;
    private final Object attachments;

    @SuppressWarnings("UnusedReturnValue")
    public String replyText(String text) {
        return AtriBot.getInstance().getMessageService().replyGroupTextMessage(this.groupOpenId, this.msgId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdownContent) {
        return AtriBot.getInstance().getMessageService().replyGroupMarkdownMessage(this.groupOpenId, this.unionOpenId, this.msgId, markdownContent);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdownContent, Object keyboard) {
        return AtriBot.getInstance().getMessageService().replyGroupMarkdownWithKeyboard(this.groupOpenId, this.unionOpenId, this.msgId, markdownContent, keyboard);
    }
}