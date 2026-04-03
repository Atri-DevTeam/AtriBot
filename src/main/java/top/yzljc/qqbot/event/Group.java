package top.yzljc.qqbot.event;

import lombok.Getter;
import top.yzljc.qqbot.botservice.message.MessageSender;

@Getter
public class Group {
    private final long groupId;

    public Group(long groupId) {
        this.groupId = groupId;
    }

    public void sendMessage(String message) {
        MessageSender.sendGroupMessage(groupId, message);
    }
}

