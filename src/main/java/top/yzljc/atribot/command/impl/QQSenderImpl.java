package top.yzljc.atribot.command.impl;

import lombok.AllArgsConstructor;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.UnsupportedPlatform;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.platform.qq.QQMessage;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName QQSenderImpl
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.botcommand.impl
 */
@AllArgsConstructor
public class QQSenderImpl implements QQCommandSender {

    private final User user;
    private final String groupId;
    private final QQMessage message;

    @Override
    public Platform getPlatform() {
        return this.user.getPlatform();
    }

    @Override
    public boolean isBot() {
        return this.user.isBot();
    }

    @Override
    public String getGroupId() {
        return this.groupId;
    }

    @Override
    public PlatformRole getRole() {
        return this.user.getRole();
    }

    @Override
    public QQMessage getMessage() {
        return this.message;
    }

    @Override
    public String getUserId() {
        return this.user.getUserId();
    }

    @Override
    public String getUsername() {
        return this.user.getUsername();
    }

    @Override
    public boolean hasPermission() {
        return this.user.hasPermission();
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.user.hasPermission(permission);
    }

    @Override
    public String sendMessage(String text) {
        if (this.user.getPlatform().equals(Platform.OFFICIAL_GROUP)) {
            return this.user.sendMessage(this.groupId, this.message.getMessageId(), text);
        } else {
            return this.user.sendMessage(this.message.getMessageId(), text);
        }
    }

    public String sendMessage(Markdown markdown) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), markdown);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(Markdown markdown)");
    }

    public String sendMessage(Markdown markdown, Object buttons) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, buttons);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), markdown, buttons);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(Markdown markdown, Object buttons)");
    }
    
    public String sendMessage(Markdown markdown, boolean at) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, at);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), markdown);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(Markdown markdown, boolean at)");
    }
    
    public String sendMessage(Markdown markdown, Object buttons, boolean at) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, buttons, at);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), markdown, buttons);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(Markdown markdown, Object buttons, boolean at)");
    }

    public String sendMessage(String image, ImageType type) {
        return sendMessage(null, image, type);
    }

    public String sendMessage(String text, String image, ImageType type) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), text, image, type);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), text, image, type, true);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(String text, String image, ImageType type)");
    }

    public String sendStreamTextMessage(List<String> textDeltas) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyTextStreamDeltas(this.user.getUserId(), this.message.getMessageId(), textDeltas);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendStreamTextMessage(List<String> textDeltas)");
    }

    public String sendStreamMarkdownMessage(List<Markdown> markdownDeltas) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyStreamDeltas(this.user.getUserId(), this.message.getMessageId(), markdownDeltas);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendStreamMarkdownMessage(List<Markdown> markdownDeltas)");
    }

    public boolean recall() {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.recall(this.groupId, this.message.getMessageId());
            }
            case OFFICIAL_C2C -> {
                return this.user.recall(this.message.getMessageId());
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "recall()");
    }

    public boolean recall(String messageId) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.recall(this.groupId, messageId);
            }
            case OFFICIAL_C2C -> {
                return this.user.recall(messageId);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "recall(String messageId)");
    }
}