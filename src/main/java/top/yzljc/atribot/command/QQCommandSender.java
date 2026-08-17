package top.yzljc.atribot.command;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.qq.QQMessage;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName QQCommandSender
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public interface QQCommandSender extends CommandSender {

    Platform getPlatform();

    boolean isBot();

    String getGroupId();

    PlatformRole getRole();

    QQMessage getMessage();

    String sendMessage(Markdown markdown);

    String sendMessage(Markdown markdown, Object buttons);

    String sendMessage(Markdown markdown, boolean at);

    String sendMessage(Markdown markdown, Object buttons, boolean at);

    String sendMessage(ImageComponent image);

    String sendStreamTextMessage(List<String> textDeltas);

    String sendStreamMarkdownMessage(List<Markdown> markdownDeltas);

    boolean recall();

    boolean recall(String messageId);
}
