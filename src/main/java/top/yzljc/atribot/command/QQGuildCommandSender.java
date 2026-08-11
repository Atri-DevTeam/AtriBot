package top.yzljc.atribot.command;

import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.qq.QQMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName QQGuildCommandSender
 * @Created_at 2026/08/14
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public interface QQGuildCommandSender extends CommandSender {

    Platform getPlatform();

    boolean isBot();

    String getGuildId();

    String getChannelId();

    String getChannelUserId();

    PlatformRole getRole();

    QQMessage getMessage();
}
