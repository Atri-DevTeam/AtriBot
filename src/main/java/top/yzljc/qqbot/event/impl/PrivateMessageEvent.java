package top.yzljc.qqbot.event.impl;

import lombok.Getter;
import top.yzljc.qqbot.event.Cancellable;
import top.yzljc.qqbot.event.Event;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.event.Sender;

import java.util.LinkedList;
public class PrivateMessageEvent extends Event implements Cancellable {
    private boolean cancelled;

    @Getter
    private final long messageId;
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

    public PrivateMessageEvent(long messageId, long userId, String rawMessage,
                             LinkedList<MessageSegment> message,
                             long time, long selfId, Sender sender) {
        this.messageId = messageId;
        this.userId = userId;
        this.rawMessage = rawMessage;
        this.message = message;
        this.time = time;
        this.selfId = selfId;
        this.sender = sender;
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
