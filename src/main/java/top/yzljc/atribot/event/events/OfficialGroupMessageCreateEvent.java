package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.platform.official.OfficialMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMessageCreateEvent
 * @Created_at 2026/05/22
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 * @Description 本事件中所有回复消息的方法均为被动
 */
@Getter
public class OfficialGroupMessageCreateEvent extends Event {
    private final User user;
    private final String groupId;
    private final OfficialMessage message;
    private final String timestamp;
    private final boolean isAtBot;

    public OfficialGroupMessageCreateEvent(User user, String groupId, OfficialMessage message, String timestamp, boolean isAtBot) {
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

    public String sendMessage(String data, ImageType type) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), data, type);
    }

    public String sendMessage(Markdown markdown, Object buttons) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, buttons);
    }
}
