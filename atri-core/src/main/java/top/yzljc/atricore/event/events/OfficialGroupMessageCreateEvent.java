package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import lombok.Getter;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.qq.QQUser;
import top.yzljc.atricore.platform.qq.QQMessage;

/**
 * 收到全量群消息 {@code GROUP_MESSAGE_CREATE} 时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMessageCreateEvent
 * @Created_at 2026/05/22
 * @Project AtriBot
 * @Package top.yzljc.atricore.event.events
 * @Description 本事件中所有回复消息的方法均为被动
 */
@Getter
public class OfficialGroupMessageCreateEvent extends Event {
    private final QQUser user;
    private final String groupId;
    private final QQMessage message;
    private final String timestamp;
    private final boolean isAtBot;

    public OfficialGroupMessageCreateEvent(QQUser user, String groupId, QQMessage message, String timestamp, boolean isAtBot) {
        this.user = user;
        this.groupId = groupId;
        this.message = message;
        this.timestamp = timestamp;
        this.isAtBot = isAtBot;
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

    public String sendMessage(ImageComponent image) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), image);
    }

    public String sendMessage(Markdown markdown, Object buttons) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, buttons);
    }

    public String sendMessage(Markdown markdown, Object buttons, boolean at) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, buttons, at);
    }

    public boolean shouldIgnore() {
        if (AtriProvider.getInstance().getAccessService().isGroupBlacklisted(this.groupId))  return true;
        return this.user.isBlocked();
    }

    public boolean isEmptyMessage() {
        if (isAtBot) {
            String replaced = this.message.getContent().replaceFirst("^<@[^>]+>\\s*", "").trim();
            return replaced.isBlank() || replaced.equals(" ");
        }
        return false;
    }
}
