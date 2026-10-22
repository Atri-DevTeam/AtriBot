package top.yzljc.atricore.command;

import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Embed;
import top.yzljc.atricore.platform.qq.QQGuildMessage;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.platform.PlatformRole;

/**
 * @Author YZ_Ljc_
 * @ClassName QQGuildCommandSender
 * @Created_at 2026/08/14
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
public interface QQGuildCommandSender extends CommandSender {

    Platform getPlatform();

    boolean isBot();

    String getGuildId();

    String getChannelId();

    /**
     * 获取 QQ 开放平台绑定的互联 ID
     *
     * @return 互联 ID，未携带时为 {@code null}
     */
    String getUnionOpenId();

    PlatformRole getRole();

    QQGuildMessage getMessage();

    String sendMessage(ImageComponent image);

    String sendMessage(Embed embed);
}
