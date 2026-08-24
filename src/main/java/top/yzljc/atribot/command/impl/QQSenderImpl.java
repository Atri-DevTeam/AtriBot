package top.yzljc.atribot.command.impl;

import lombok.AllArgsConstructor;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.event.EventType;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.UnsupportedPlatform;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.platform.qq.QQMessage;

import java.util.List;
import java.util.Objects;

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
            if (this.message.getMessageEventType().equals(EventType.OFFICIAL_GROUP_MESSAGE)) {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), text, this.message.getRefIdx());
            } else {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), text);
            }
        } else {
            return this.user.sendMessage(this.message.getMessageId(), text);
        }
    }

    @Override
    public String sendMessage(String text, boolean ref) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), text, this.message.getRefIdx());
            }
            case OFFICIAL_C2C -> {
                return C2CChat.refMessage(this.user.getUserId(), this.message.getRefIdx(), text);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(String text, boolean ref)");
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public String sendMessage(ImageComponent image) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), image);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), image);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(ImageComponent image)");
    }

    @Override
    public String sendStreamTextMessage(List<String> textDeltas) {
        if (Objects.requireNonNull(this.user.getPlatform()) == Platform.OFFICIAL_C2C) {
            return C2CChat.replyTextStreamDeltas(this.user.getUserId(), this.message.getMessageId(), textDeltas);
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendStreamTextMessage(List<String> textDeltas)");
    }

    @Override
    public String sendStreamMarkdownMessage(List<Markdown> markdownDeltas) {
        if (Objects.requireNonNull(this.user.getPlatform()) == Platform.OFFICIAL_C2C) {
            return C2CChat.replyStreamDeltas(this.user.getUserId(), this.message.getMessageId(), markdownDeltas);
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendStreamMarkdownMessage(List<Markdown> markdownDeltas)");
    }

    @Override
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

    @Override
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
