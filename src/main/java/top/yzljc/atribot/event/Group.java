package top.yzljc.atribot.event;

import top.yzljc.atribot.chat.onebot.impl.MessageSegment;
import top.yzljc.atribot.chat.onebot.GroupMessage;

import java.util.List;

public record Group(long groupId) {

    public void sendSingleText(String message) {
        GroupMessage.chatMessage(groupId, message);
    }

    public void sendUnionMessage(List<MessageSegment> data) {
        GroupMessage.chatMessage(groupId, data);
    }
}

