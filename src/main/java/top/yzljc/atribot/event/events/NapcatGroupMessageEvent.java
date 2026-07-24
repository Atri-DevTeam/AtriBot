package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.platform.napcat.NapcatMessage;

import java.util.List;


@Getter
public class NapcatGroupMessageEvent extends Event {
    private final User user;
    private final NapcatMessage message;
    private final String groupId;
    private final String timestamp;

    public NapcatGroupMessageEvent(User user, NapcatMessage message, String groupId, String timestamp) {
        this.user = user;
        this.message = message;
        this.groupId = groupId;
        this.timestamp = timestamp;
    }

    public String sendMessage(String content) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), content);
    }

    public String sendMessage(List<MessageSegment> data) {
        return GroupMessage.chatMessage(this.groupId, data);
    }

    public void recall() {
        this.user.recall(this.groupId, this.message.getMessageId());
    }
}
