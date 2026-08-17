package top.yzljc.atribot.command;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.napcat.NapcatMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName NapcatCommandSender
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public interface NapcatCommandSender extends CommandSender {

    Platform getPlatform();

    boolean isBot();

    String getGroupId();

    PlatformRole getRole();

    NapcatMessage getMessage();

    String sendMessage(ImageComponent image);

    boolean recall();

    boolean recall(String messageId);
}
