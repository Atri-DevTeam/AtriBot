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

    @SuppressWarnings("UnusedReturnValue")
    public String reply(String text) {
        return String.valueOf(GroupMessage.replyMessage(userId, groupId, messageId, false, text));
    }

    @SuppressWarnings("UnusedReturnValue")
    public String reply(String text, boolean atUser) {
        return String.valueOf(GroupMessage.replyMessage(userId, groupId, messageId, atUser, text));
    }

    // 以下方法均为官机使用

    @SuppressWarnings("UnusedReturnValue")
    public String officialGroupReplyMarkdown(String content) {
        return AtriBot.getInstance().getMessageService().replyGroupMarkdownMessage(this.groupOpenId, this.userOpenId, this.messageOpenId, content);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialGroupReplyMarkdown(String content, Object keyboard) {
        return AtriBot.getInstance().getMessageService().replyGroupMarkdownWithKeyboard(this.groupOpenId, this.userOpenId, this.messageOpenId, content, keyboard);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialGroupReplyText(String content) {
        return AtriBot.getInstance().getMessageService().replyGroupTextMessage(this.groupOpenId, this.messageOpenId, content);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialPrivateReplyMarkdown(String content) {
        return AtriBot.getInstance().getMessageService().replyPrivateMarkdownMessage(this.userOpenId, this.messageOpenId, content);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialPrivateReplyMarkdown(String content, Object keyboard) {
        return AtriBot.getInstance().getMessageService().replyPrivateMarkdownWithKeyboard(this.userOpenId, this.messageOpenId, content, keyboard);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialPrivateReplyText(String content) {
        return AtriBot.getInstance().getMessageService().replyPrivateTextMessage(this.userOpenId, this.messageOpenId, content);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String label, String content) {
        if (label.equals("0")) throw new UnsupportedOperationException("第三方机器人不支持Markdown消息喵");
        switch (label) {
            case "1" -> { return officialPrivateReplyMarkdown(content); }
            case "2" -> { return officialGroupReplyMarkdown(content); }
            default -> throw new IllegalArgumentException("未知的消息类型: " + label);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String label, String content, Object keyboard) {
        if (label.equals("0")) throw new UnsupportedOperationException("第三方机器人不支持Markdown消息喵");
        switch (label) {
            case "1" -> { return officialPrivateReplyMarkdown(content, keyboard); }
            case "2" -> { return officialGroupReplyMarkdown(content, keyboard); }
            default -> throw new IllegalArgumentException("未知的消息类型: " + label);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyText(String label, String content) {
        switch (label) {
            case "0" -> { return reply(content, true); }
            case "1" -> { return officialPrivateReplyText(content); }
            case "2" -> { return officialGroupReplyText(content); }
            default -> throw new IllegalArgumentException("未知的消息类型: " + label);
        }
    }
}