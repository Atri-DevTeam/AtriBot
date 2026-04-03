package top.yzljc.qqbot.command;

import top.yzljc.qqbot.botservice.message.MessageUtils;

public record CommandSender(long userId, long groupId, boolean isAdmin, boolean isDebug, long messageId) {

    public void reply(String text, boolean atUser) {
        MessageUtils.replyMessage(userId, groupId, messageId, atUser, text);
    }
}