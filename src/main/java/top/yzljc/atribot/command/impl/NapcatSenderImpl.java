package top.yzljc.atribot.command.impl;

import lombok.AllArgsConstructor;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.UnsupportedPlatform;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.platform.napcat.NapcatMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName NapcatSenderImpl
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.botcommand.impl
 */
@AllArgsConstructor
public class NapcatSenderImpl implements NapcatCommandSender {

    private final User user;
    private final String groupId;
    private final NapcatMessage message;

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
    public NapcatMessage getMessage() {
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
        if (this.user.getPlatform().equals(Platform.NAPCAT_GROUP)) {
            return this.user.sendMessage(this.groupId, this.message.getMessageId(), text);
        } else {
            return this.user.sendMessage(this.user.getUserId(), this.message.getMessageId(), text);
        }
    }

    public String sendMessage(ImageComponent image) {
        switch (this.user.getPlatform()) {
            case NAPCAT_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), image);
            }
            case NAPCAT_PRIVATE -> {
                return this.user.sendMessage(this.message.getMessageId(), image);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(ImageComponent image)");
    }

    public boolean recall() {
        return recall(this.message.getMessageId());
    }

    public boolean recall(String messageId) {
        switch (this.user.getPlatform()) {
            case NAPCAT_GROUP -> {
                return this.user.recall(this.groupId, messageId);
            }
            case NAPCAT_PRIVATE -> {
                return this.user.recall(messageId);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "recall(String messageId)");
    }
}
