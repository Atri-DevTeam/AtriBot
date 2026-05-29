package top.yzljc.atribot.event.impl;

import lombok.Getter;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.event.Author;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficePrivateChatEvent
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class OfficialC2CMessageEvent extends Event {
    private final String msgId;
    private final String content;
    private final String timestamp;
    private final Object attachments;
    private final Author author;

    /**
     * @param userOpenId Author 的 memberOpenId 此处使用 userOpenId 以兼容完整字段，但数据内容以使用 userOpenId 为准
     */
    public OfficialC2CMessageEvent(boolean bot, String userOpenId, String username, String id,
                                   String msgId, String content, String timestamp, String unionOpenId,
                                   Object attachments) {
        this.msgId = msgId;
        this.content = content;
        this.timestamp = timestamp;
        this.attachments = attachments;
        this.author = new Author(bot, id, userOpenId, unionOpenId, username);
    }

    /**
     * @deprecated 使用 {@link #getAuthor()}.{@link Author#getUnionOpenId() getUnionOpenId()}
     */
    @Deprecated
    public String getUnionOpenId() {
        return author.getUnionOpenId();
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyText(String text) {
        return Atri.getInstance().getChatService().replyPrivateTextMessage(this.author.getUnionOpenId(), this.msgId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdown) {
        return Atri.getInstance().getChatService().replyPrivateMarkdownMessage(this.author.getUnionOpenId(), this.msgId, markdown);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdown, Object keyboard) {
        return Atri.getInstance().getChatService().replyPrivateMarkdownMessage(this.author.getUnionOpenId(), this.msgId, markdown, keyboard);
    }
}
