package top.yzljc.qqbot.event;

import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.chat.SendGroupMessage;

import java.util.List;

public record Group(long groupId) {

    public void sendSingleText(String message) {
        MessageSender.sendGroupMessage(groupId, message);
    }

    public void sendUnionMessage(List<MessageSegment> data) {
        SendGroupMessage.unionChatMessage(groupId, data);
    }
}

