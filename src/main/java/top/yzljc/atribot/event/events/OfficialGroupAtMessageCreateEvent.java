package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.User;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupCommandEvent
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class OfficialGroupAtMessageCreateEvent extends Event {
    private final User user;
    private final Message message;
    private final String groupId;
    private final String timestamp;

    public OfficialGroupAtMessageCreateEvent(User user, Message message, String groupId, String timestamp) {
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
}
