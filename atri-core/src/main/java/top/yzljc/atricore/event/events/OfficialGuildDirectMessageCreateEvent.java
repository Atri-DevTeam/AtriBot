package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.qq.QQGuildMessage;
import top.yzljc.atricore.platform.qq.QQGuildUser;

/**
 * 收到频道私信 {@code DIRECT_MESSAGE_CREATE} 时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGuildDirectMessageCreateEvent
 * @Created_at 2026/08/08
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialGuildDirectMessageCreateEvent extends Event {
    private final QQGuildUser user;
    /**
     * QQ 开放平台绑定的互联 ID，取自 {@code author.union_openid}，未携带时为 {@code null}
     */
    private final String unionOpenId;
    private final String guildId;
    private final String channelId;
    private final QQGuildMessage message;

    public String replyMessage(String content) {
        return user.sendMessage(this.guildId, this.message.getMessageId(), content);
    }

    public String replyMessage(ImageComponent image) {
        return user.sendMessage(this.guildId, this.message.getMessageId(), image);
    }
}