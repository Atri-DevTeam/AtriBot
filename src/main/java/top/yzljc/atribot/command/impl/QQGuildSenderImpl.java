package top.yzljc.atribot.command.impl;

import lombok.AllArgsConstructor;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.qq.QQMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName QQGuildSenderImpl
 * @Created_at 2026/08/14
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command.impl
 */
@AllArgsConstructor
public class QQGuildSenderImpl implements QQGuildCommandSender {

    private final Platform platform;
    private final QQMessage message;
    private final String guildId;
    private final String channelId;
    private final String channelUserId;

    @Override
    public Platform getPlatform() {
        return null;
    }

    @Override
    public boolean isBot() {
        return false;
    }

    @Override
    public String getGuildId() {
        return "";
    }

    @Override
    public String getChannelId() {
        return "";
    }

    @Override
    public String getChannelUserId() {
        return "";
    }

    @Override
    public PlatformRole getRole() {
        return null;
    }

    @Override
    public QQMessage getMessage() {
        return null;
    }

    @Override
    public String getUserId() {
        return "";
    }

    @Override
    public String getUsername() {
        return "";
    }

    @Override
    public boolean hasPermission() {
        return false;
    }

    @Override
    public boolean hasPermission(String permission) {
        return false;
    }

    @Override
    public String sendMessage(String text) {
        return "";
    }
}