package top.yzljc.atribot.command.impl;

import lombok.AllArgsConstructor;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.User;

/**
 * @Author YZ_Ljc_
 * @ClassName QQGuildSenderImpl
 * @Created_at 2026/08/14
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command.impl
 */
@AllArgsConstructor
public class QQGuildSenderImpl implements QQGuildCommandSender {

    private final User user;
    private final Message message;
    private final String guildId;
    private final String channelId;
    private final String userOpenId;

    @Override
    public Platform getPlatform() {
        return this.user.getPlatform();
    }

    @Override
    public boolean isBot() {
        return this.user.isBot();
    }

    @Override
    public String getGuildId() {
        return this.guildId;
    }

    @Override
    public String getChannelId() {
        return this.channelId;
    }

    @Override
    public String getUserOpenId() {
        return this.userOpenId;
    }

    @Override
    public PlatformRole getRole() {
        return this.user.getRole();
    }

    @Override
    public Message getMessage() {
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
        return OfficialUsers.isAdmin(this.userOpenId);
    }

    @Override
    public boolean hasPermission(String permission) {
        return OfficialUsers.hasPermission(this.userOpenId, permission);
    }

    @Override
    public String sendMessage(String text) {
        if (this.user.getPlatform() == Platform.OFFICIAL_GUILD_CHANNEL) {
            return this.user.sendMessage(this.channelId, this.message.getMessageId(), text);
        } else {
            return this.user.sendMessage(this.guildId, this.message.getMessageId(), text);
        }
    }

    @Override
    public String sendMessage(ImageComponent image) {
        if (this.user.getPlatform() == Platform.OFFICIAL_GUILD_CHANNEL) {
            return this.user.sendMessage(this.channelId, this.message.getMessageId(), image);
        } else {
            return this.user.sendMessage(this.guildId, this.message.getMessageId(), image);
        }
    }
}