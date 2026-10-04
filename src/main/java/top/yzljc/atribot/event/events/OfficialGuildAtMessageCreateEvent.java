package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.qq.QQGuildMessage;
import top.yzljc.atribot.platform.qq.QQGuildUser;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGuildAtMessageCreateEvent
 * @Created_at 2026/08/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
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