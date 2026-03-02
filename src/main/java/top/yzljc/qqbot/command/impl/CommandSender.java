package top.yzljc.qqbot.command.impl;

import top.yzljc.qqbot.botservice.tools.MT;

public class CommandSender {
    private final long userId;
    private final long groupId;
    private final boolean isAdmin;
    private final boolean isDebug;
    private final int messageId;

    public CommandSender(long userId, long groupId, boolean isAdmin, boolean isDebug, int messageId) {
        this.userId = userId;
        this.groupId = groupId;
        this.isAdmin = isAdmin;
        this.isDebug = isDebug;
        this.messageId = messageId;
    }

    public long getUserId() {
        return userId;
    }

    public long getGroupId() {
        return groupId;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public boolean isDebug() {
        return isDebug;
    }

    public int getMessageId() {
        return messageId;
    }

    public void reply(String text, boolean atUser) {
        MT.replyMessage(userId, groupId, messageId, atUser, text);
    }
}