package top.yzljc.qqbot.event.impl;

import lombok.Getter;
import top.yzljc.qqbot.chat.impl.MessageUtils;
import top.yzljc.qqbot.event.Cancellable;
import top.yzljc.qqbot.event.Event;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.event.Sender;
import top.yzljc.qqbot.event.Group;

import java.util.LinkedList;

public class GroupMessageEvent extends Event implements Cancellable {
    private boolean cancelled;

    @Getter
    private final long messageId;
    @Getter
    private final long groupId;
    @Getter
    private final long userId;
    @Getter
    private final String rawMessage;
    @Getter
    private final LinkedList<MessageSegment> message;
    @Getter
    private final long time;
    @Getter
    private final long selfId;
    @Getter
    private final Sender sender;
    @Getter
    private final Group group;

    public GroupMessageEvent(long messageId, long groupId, long userId, String rawMessage,
                             LinkedList<MessageSegment> message,
                             long time, long selfId, Sender sender) {
        this.messageId = messageId;
        this.groupId = groupId;
        this.userId = userId;
        this.rawMessage = rawMessage;
        this.message = message;
        this.time = time;
        this.selfId = selfId;
        this.sender = sender;
        if (this.sender != null) {
            this.sender.setReplyGroupId(groupId);
        }
        if (this.sender != null) {
            this.sender.setReplyMessageId(messageId);
        }
        this.group = new Group(groupId);
    }

    public void recall() {
        MessageUtils.recallMessage(this.messageId);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
