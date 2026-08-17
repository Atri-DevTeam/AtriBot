package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.User;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGuildDirectMessageCreateEvent
 * @Created_at 2026/08/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialGuildDirectMessageCreateEvent extends Event {
    private final User user;
    private final String userOpenId;
    private final String guildId;
    private final String channelId;
    private final Message message;

    public String replyMessage(String content) {
        return user.sendMessage(this.guildId, this.message.getMessageId(), content);
    }

    public String replyMessage(ImageComponent image) {
        return user.sendMessage(this.guildId, this.message.getMessageId(), image);
    }
}