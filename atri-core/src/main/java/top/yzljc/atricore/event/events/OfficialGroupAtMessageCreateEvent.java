package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import lombok.Getter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.card.Card;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.qq.QQUser;
import top.yzljc.atricore.platform.qq.QQMessage;

/**
 * 收到 {@code GROUP_AT_MESSAGE_CREATE}，即群内 @ 机器人的消息时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupAtMessageCreateEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
public class OfficialGroupAtMessageCreateEvent extends Event {
    private final QQUser user;
    private final QQMessage message;
    private final String groupId;
    private final String timestamp;

    public OfficialGroupAtMessageCreateEvent(QQUser user, QQMessage message, String groupId, String timestamp) {
        this.user = user;
        this.message = message;
        this.groupId = groupId;
        this.timestamp = timestamp;
    }

    /**
     * 使用当前群 @ 消息 ID，向当前群发送被动卡片回复
     *
     * @param card 卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws top.yzljc.atricore.chat.official.QQMessageSendException QQ 开放平台拒绝发送消息
     */
    public String sendMessage(Card card) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), card);
    }

    /**
     * 被动消息回复 - 纯文本消息
     *
     * @param content 文本消息内容
     * @return 平台返回的消息 ID
     */
    public String sendMessage(String content) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), content);
    }

    /**
     * 使用当前群 @ 消息 ID 发送图片回复
     *
     * @param image 图片组件
     * @return 平台返回的消息 ID，上传或发送失败时返回 null
     */
    public String sendMessage(ImageComponent image) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), image);
    }

    /**
     * 被动消息回复 - Markdown 消息
     * <p>被动消息的 Markdown内容会自动携带一行 {@code <@UserOpenId>} 且独占一行，如需关闭请用 {@link OfficialGroupAtMessageCreateEvent#sendMessage(Markdown, boolean)}</p>
     *
     * @param markdown Markdown 消息内容
     * @return 平台返回的消息 ID
     */
    public String sendMessage(Markdown markdown) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown);
    }

    /**
     * 被动消息回复 - Markdown 消息（关闭自动 @）
     *
     * @param markdown Markdown 消息内容
     * @param at       是否自动 @ 消息发送者
     * @return 平台返回的消息 ID
     */
    public String sendMessage(Markdown markdown, boolean at) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, at);
    }

    public boolean shouldIgnore() {
        if (AtriProvider.getInstance().getAccessService().isGroupBlacklisted(this.groupId)) return true;
        return this.user.isBlocked();
    }
}
