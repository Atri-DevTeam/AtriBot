package top.yzljc.atricore.command;

import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.platform.PlatformRole;
import top.yzljc.atricore.platform.napcat.NapcatMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName NapcatCommandSender
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
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
