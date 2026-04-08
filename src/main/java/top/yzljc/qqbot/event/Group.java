package top.yzljc.qqbot.event;

import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.chat.GroupMessage;

import java.util.List;

public record Group(long groupId) {

    public void sendSingleText(String message) {
        GroupMessage.chatMessage(groupId, message);
    }

    public void sendUnionMessage(List<MessageSegment> data) {
        GroupMessage.chatMessage(groupId, data);
    }
}

