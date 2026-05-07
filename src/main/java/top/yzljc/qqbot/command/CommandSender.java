package top.yzljc.qqbot.command;

import top.yzljc.qqbot.chat.GroupMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandSender
 * @Created_at 2026/05/07
 * @Project AtriBot
 * @Package top.yzljc.qqbot.command
 */
public record CommandSender(long userId, long groupId, boolean isAdmin, boolean isDebug, long messageId,
        String groupOpenId, String userOpenId, String messageOpenId) {

    public CommandSender {
    }

    public static CommandSender of(long userId, long groupId, boolean isAdmin, boolean isDebug, long messageId) {
        return new CommandSender(userId, groupId, isAdmin, isDebug, messageId, null, null, null);
    }

    public static CommandSender of(String userOpenId, String groupOpenId, boolean isAdmin, boolean isDebug, String messageOpenId) {
        return new CommandSender(-1, -1, isAdmin, isDebug, -1, groupOpenId, userOpenId, messageOpenId);
    }

    public static CommandSender of(String userOpenId, boolean isAdmin, boolean isDebug, String messageOpenId) {
        return new CommandSender(-1, -1, isAdmin, isDebug, -1, null, userOpenId, messageOpenId);
    }

    public void reply(String text, boolean atUser) {
        GroupMessage.replyMessage(userId, groupId, messageId, atUser, text);
    }
}