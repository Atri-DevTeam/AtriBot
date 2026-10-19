package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import lombok.Getter;
import top.yzljc.atricore.chat.official.Markdown;
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

    public String sendMessage(String content) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), content);
    }

    public String sendMessage(Markdown markdown) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown);
    }

    public String sendMessage(Markdown markdown, boolean at) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, at);
    }

    public String sendMessage(Markdown markdown, Object keyboard) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, keyboard);
    }

    public String sendMessage(Markdown markdown, Object keyboard, boolean at) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, keyboard, at);
    }

    public boolean shouldIgnore() {
        if (AtriProvider.getInstance().getAccessService().isGroupBlacklisted(this.groupId)) return true;
        return this.user.isBlocked();
    }
}
