package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.qq.QQGuildMessage;
import top.yzljc.atricore.platform.qq.QQGuildUser;

/**
 * 收到文字子频道 @ 消息 {@code AT_MESSAGE_CREATE} 时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGuildAtMessageCreateEvent
 * @Created_at 2026/08/06
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialGuildAtMessageCreateEvent extends Event {
    private final QQGuildUser user;
    private final String userOpenId;
    private final String guildId;
    private final String channelId;
    private final QQGuildMessage message;

    public String replyMessage(String content) {
        return user.sendMessage(this.channelId, this.message.getMessageId(), content);
    }

    public String replyMessage(ImageComponent image) {
        return user.sendMessage(this.channelId, this.message.getMessageId(), image);
    }

    public boolean isEmptyMessage() {
        return this.message.getContent().isBlank();
    }
}