package top.yzljc.atribot.event.impl;

import lombok.Getter;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.event.Author;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupCommandEvent
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class OfficialGroupAtMessageCreateEvent extends Event {
    private final String msgId;
    // 此字段意义不明，但官方接口中确实存在，暂时保留
    private final String groupId;
    private final String groupOpenId;
    private final String content;
    private final String timestamp;
    private final Object attachments;
    private final Object messageReference;
    private final Author author;

    public OfficialGroupAtMessageCreateEvent(boolean bot, String id, String msgId, String groupId, String groupOpenId,
                                             String content, String timestamp, String username, String memberOpenId,
                                             String unionOpenId, Object attachments, Object messageReference) {
        this.msgId = msgId;
        this.groupId = groupId;
        this.groupOpenId = groupOpenId;
        this.content = content;
        this.timestamp = timestamp;
        this.attachments = attachments;
        this.messageReference = messageReference;
        this.author = new Author(bot, id, memberOpenId, unionOpenId, username);
    }

    /**
     * @deprecated 使用 {@link #getAuthor()}.{@link Author#getUnionOpenId() getUnionOpenId()}
     */
    @Deprecated
    public String getUnionOpenId() {
        return author.getUnionOpenId();
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text) {
        return Atri.getInstance().getChatService().replyGroupTextMessage(this.groupOpenId, this.msgId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown markdownContent) {
        return Atri.getInstance().getChatService().replyGroupMarkdownMessage(this.groupOpenId, this.author.getUnionOpenId(), this.msgId, markdownContent);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown markdownContent, Object keyboard) {
        return Atri.getInstance().getChatService().replyGroupMarkdownMessage(this.groupOpenId, this.author.getUnionOpenId(), this.msgId, markdownContent, keyboard);
    }
}
