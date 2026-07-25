package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.platform.official.OfficialMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupCommandEvent
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 * @Description 本事件中所有回复消息的方法均为被动
 */
@Getter
public class OfficialGroupAtMessageCreateEvent extends Event {
    private final User user;
    private final OfficialMessage message;
    private final String groupId;
    private final String timestamp;

    public OfficialGroupAtMessageCreateEvent(User user, OfficialMessage message, String groupId, String timestamp) {
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

    public boolean shouldIgnore() {
        if (OfficialGroups.isGroupBlacklisted(this.groupId)) return true;
        return this.user.isBlocked();
    }
}
