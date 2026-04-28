package top.yzljc.qqbot.command;

import top.yzljc.qqbot.chat.GroupMessage;

public record CommandSender(long userId, long groupId, boolean isAdmin, boolean isDebug, long messageId) {

    public void reply(String text, boolean atUser) {
        GroupMessage.replyMessage(userId, groupId, messageId, atUser, text);
    }
}