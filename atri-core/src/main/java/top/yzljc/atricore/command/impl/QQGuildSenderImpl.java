package top.yzljc.atricore.command.impl;

import top.yzljc.atricore.AtriProvider;
import lombok.AllArgsConstructor;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Embed;
import top.yzljc.atricore.chat.official.GuildChannelChat;
import top.yzljc.atricore.chat.official.GuildDirectChat;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.command.QQGuildCommandSender;
import top.yzljc.atricore.platform.qq.QQGuildMessage;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.platform.PlatformRole;
import top.yzljc.atricore.platform.qq.QQGuildUser;

/**
 * @Author YZ_Ljc_
 * @ClassName QQGuildSenderImpl
 * @Created_at 2026/08/14
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command.impl
 */
@AllArgsConstructor
public class QQGuildSenderImpl implements QQGuildCommandSender {

    private final QQGuildUser user;
    private final QQGuildMessage message;
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
    public QQGuildMessage getMessage() {
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
        return AtriProvider.getInstance().getAccessService().isAdmin(this.userOpenId);
    }

    @Override
    public boolean hasPermission(String permission) {
        return AtriProvider.getInstance().getAccessService().hasPermission(this.userOpenId, permission);
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

    @Override
    public String sendMessage(Embed embed) {
        if (this.user.getPlatform() == Platform.OFFICIAL_GUILD_CHANNEL) {
            return GuildChannelChat.replyMessage(this.channelId, RT.message(this.message.getMessageId()), embed);
        } else {
            return GuildDirectChat.replyMessage(this.guildId, RT.message(this.message.getMessageId()), embed);
        }
    }
}