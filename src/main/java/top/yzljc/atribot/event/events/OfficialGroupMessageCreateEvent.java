package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.User;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMessageCreateEvent
 * @Created_at 2026/05/22
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class OfficialGroupMessageCreateEvent extends Event {
    private final User user;
    private final String groupId;
    private final Message message;
    private final String timestamp;
    private final boolean isAtBot;

    public OfficialGroupMessageCreateEvent(User user, String groupId, Message message, String timestamp, boolean isAtBot) {
        this.user = user;
        this.groupId = groupId;
        this.message = message;
        this.timestamp = timestamp;
        this.isAtBot = isAtBot;
    }

    public String sendMessage(String content) {
        return this.user.sendMessage(this.message.getMessageId(), content);
    }

    public String sendMessage(Markdown markdown) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown);
    }

    public String sendMessage(String data, ImageType type) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), data, type);
    }

    public String sendMessage(Markdown markdown, Object buttons) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, buttons);
    }
}