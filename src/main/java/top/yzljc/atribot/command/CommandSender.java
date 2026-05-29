package top.yzljc.atribot.command;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.ImageType;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.event.Author;
import top.yzljc.atribot.functions.official.permission.PermissionGroup;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandSender
 * @Created_at 2026/05/07
 * @Project AtriBot
 * @Package top.yzljc.qqbot.command
 */
public record CommandSender(long userId, long groupId, boolean isAdmin, boolean isDebug, long messageId,
                            String groupOpenId, String unionOpenId, String messageOpenId, Author author,
                            String label) {

    public static CommandSender of(long userId, long groupId, boolean isAdmin, boolean isDebug, long messageId, String label) {
        return new CommandSender(userId, groupId, isAdmin, isDebug, messageId, null, null, null, null, label);
    }

    public static CommandSender of(String userOpenId, String groupOpenId, boolean isAdmin, boolean isDebug, String messageOpenId, String label) {
        return new CommandSender(-1, -1, isAdmin, isDebug, -1, groupOpenId, userOpenId, messageOpenId, null, label);
    }

    public static CommandSender of(String userOpenId, String groupOpenId, boolean isAdmin, boolean isDebug, String messageOpenId, Author author, String label) {
        return new CommandSender(-1, -1, isAdmin, isDebug, -1, groupOpenId, userOpenId, messageOpenId, author, label);
    }

    public static CommandSender of(String userOpenId, boolean isAdmin, boolean isDebug, String messageOpenId, String label) {
        return new CommandSender(-1, -1, isAdmin, isDebug, -1, null, userOpenId, messageOpenId, null, label);
    }

    public static CommandSender of(String userOpenId, boolean isAdmin, boolean isDebug, String messageOpenId, Author author, String label) {
        return new CommandSender(-1, -1, isAdmin, isDebug, -1, null, userOpenId, messageOpenId, author, label);
    }

    @SuppressWarnings("UnusedReturnValue")
    public long reply(String text) {
        return GroupMessage.replyMessage(userId, groupId, messageId, false, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public long reply(String text, boolean atUser) {
        return GroupMessage.replyMessage(userId, groupId, messageId, atUser, text);
    }

    // 以下方法均为官机使用

    @SuppressWarnings("UnusedReturnValue")
    public String officialGroupReplyMarkdown(String content) {
        return Atri.getInstance().getChatService().replyGroupMarkdownMessage(this.groupOpenId, this.unionOpenId, this.messageOpenId, content);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialGroupReplyMarkdown(String content, Object keyboard) {
        return Atri.getInstance().getChatService().replyGroupMarkdownMessage(this.groupOpenId, this.unionOpenId, this.messageOpenId, content, keyboard);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialGroupReplyText(String content) {
        return Atri.getInstance().getChatService().replyGroupTextMessage(this.groupOpenId, this.messageOpenId, content);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialPrivateReplyMarkdown(String content) {
        return Atri.getInstance().getChatService().replyPrivateMarkdownMessage(this.unionOpenId, this.messageOpenId, content);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialPrivateReplyMarkdown(String content, Object keyboard) {
        return Atri.getInstance().getChatService().replyPrivateMarkdownMessage(this.unionOpenId, this.messageOpenId, content, keyboard);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialPrivateReplyText(String content) {
        return Atri.getInstance().getChatService().replyPrivateTextMessage(this.unionOpenId, this.messageOpenId, content);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialReplyGroupImage(String url, ImageType type) {
        return Atri.getInstance().getChatService().replyGroupImageMessage(this.groupOpenId, this.messageOpenId, type, url);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String officialReplyPrivateImage(String url, ImageType type) {
        return Atri.getInstance().getChatService().replyPrivateImageMessage(this.unionOpenId, this.messageOpenId, type, url);
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
            case "1" -> { return officialPrivateReplyText(content); }
            case "2" -> { return officialGroupReplyText(content); }
            default -> throw new IllegalArgumentException("未知的消息类型: " + label);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyImage(String label, String url, ImageType type) {
        switch (label) {
            case "1" -> { return officialReplyPrivateImage(url, type); }
            case "2" -> { return officialReplyGroupImage(url, type); }
            default -> throw new IllegalArgumentException("未知的消息类型: " + label);
        }
    }

    public boolean hasPermission(String permission) {
        if (isAdmin) return true;
        return PermissionGroup.hasPermission(this.unionOpenId, permission);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String content) {
        switch (label) {
            case "1" -> {
                return officialPrivateReplyText(content);
            }
            case "2" -> {
                return officialGroupReplyText(content);
            }
        }
        throw new UnsupportedOperationException("你使用了一个不支持的消息类型，该函数仅供官方机器人使用，类型：" + label);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown content) {
        switch (label) {
            case "1" -> {
                return officialPrivateReplyMarkdown(content.getText());
            }
            case "2" -> {
                return officialGroupReplyMarkdown(content.getText());
            }
        }
        throw new UnsupportedOperationException("你使用了一个不支持的消息类型，该函数仅供官方机器人使用，类型：" + label);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown content, Object keyboard) {
        switch (label) {
            case "1" -> {
                return officialPrivateReplyMarkdown(content.getText(), keyboard);
            }
            case "2" -> {
                return officialGroupReplyMarkdown(content.getText(), keyboard);
            }
        }
        throw new UnsupportedOperationException("你使用了一个不支持的消息类型，该函数仅供官方机器人使用，类型：" + label);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String attachmentData, ImageType type) {
        switch (label) {
            case "1" -> {
                return officialReplyPrivateImage(attachmentData, type);
            }
            case "2" -> {
                return officialReplyGroupImage(attachmentData, type);
            }
        }
        throw new UnsupportedOperationException("你使用了一个不支持的消息类型，该函数仅供官方机器人使用，类型：" + label);
    }
}