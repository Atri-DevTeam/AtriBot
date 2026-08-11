package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.ImageType;
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
    private final String guildId;
    private final String channelId;
    private final Message message;

    public String replyMessage(String content) {
        return user.sendMessage(this.guildId, this.message.getMessageId(), content);
    }

    public String replyMessage(String text, String imageUrl) {
        return user.sendMessage(this.guildId, this.message.getMessageId(), text, imageUrl, ImageType.URL);
    }
}