package top.yzljc.qqbot.command;

import top.yzljc.qqbot.AtriBot;
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

    public static CommandSender of(long userId, long groupId, boolean isAdmin, boolean isDebug, long messageId) {
        return new CommandSender(userId, groupId, isAdmin, isDebug, messageId, null, null, null);
    }

    public static CommandSender of(String userOpenId, String groupOpenId, boolean isAdmin, boolean isDebug, String messageOpenId) {
        return new CommandSender(-1, -1, isAdmin, isDebug, -1, groupOpenId, userOpenId, messageOpenId);
    }

    public static CommandSender of(String userOpenId, boolean isAdmin, boolean isDebug, String messageOpenId) {
        return new CommandSender(-1, -1, isAdmin, isDebug, -1, null, userOpenId, messageOpenId);
    }

    public void reply(String text) {
        GroupMessage.replyMessage(userId, groupId, messageId, false, text);
    }

    public void reply(String text, boolean atUser) {
        GroupMessage.replyMessage(userId, groupId, messageId, atUser, text);
    }

    // 以下方法均为官机使用

    public void officialGroupReplyMarkdown(String content) {
        AtriBot.getInstance().getMessageService().replyGroupMarkdownMessage(this.groupOpenId, this.messageOpenId, content);
    }

    public void officialGroupReplyMarkdown(String content, Object keyboard) {
        AtriBot.getInstance().getMessageService().replyGroupMarkdownWithKeyboard(this.groupOpenId, this.messageOpenId, content, keyboard);
    }

    public void officialGroupReplyText(String content) {
        AtriBot.getInstance().getMessageService().replyGroupTextMessage(this.groupOpenId, this.messageOpenId, content);
    }

    public void officialPrivateReplyMarkdown(String content) {
        AtriBot.getInstance().getMessageService().replyPrivateMarkdownMessage(this.userOpenId, this.messageOpenId, content);
    }

    public void officialPrivateReplyMarkdown(String content, Object keyboard) {
        AtriBot.getInstance().getMessageService().replyPrivateMarkdownWithKeyboard(this.userOpenId, this.messageOpenId, content, keyboard);
    }

    public void officialPrivateReplyText(String content) {
        AtriBot.getInstance().getMessageService().replyPrivateTextMessage(this.userOpenId, this.messageOpenId, content);
    }

    public void replyMarkdown(String label, String content) {
        if (label.equals("0")) throw new UnsupportedOperationException("第三方机器人不支持Markdown消息喵");
        switch (label) {
            case "1" -> officialPrivateReplyMarkdown(content);
            case "2" -> officialGroupReplyMarkdown(content);
            default -> throw new IllegalArgumentException("未知的消息类型: " + label);
        }
    }

    public void replyMarkdown(String label, String content, Object keyboard) {
        if (label.equals("0")) throw new UnsupportedOperationException("第三方机器人不支持Markdown消息喵");
        switch (label) {
            case "1" -> officialPrivateReplyMarkdown(content, keyboard);
            case "2" -> officialGroupReplyMarkdown(content, keyboard);
            default -> throw new IllegalArgumentException("未知的消息类型: " + label);
        }
    }

    public void replyText(String label, String content) {
        switch (label) {
            case "0" -> reply(content, true);
            case "1" -> officialPrivateReplyText(content);
            case "2" -> officialGroupReplyText(content);
            default -> throw new IllegalArgumentException("未知的消息类型: " + label);
        }
    }
}