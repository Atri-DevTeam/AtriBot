package top.yzljc.atribot.command;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.kook.KookCard;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.kook.KookMessage;

import java.nio.file.Path;

/**
 * @Author YZ_Ljc_
 * @ClassName KookCommandSender
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public interface KookCommandSender extends CommandSender {
    Platform getPlatform();
    String getGuildId();
    String getChannelId();
    KookMessage getMessage();
    String sendKMarkdown(String text);
    String sendCard(KookCard... cards);
    /**
     * 上传图片组件并发送纯图片消息；URL 先下载，Base64 先解码，附带文字另发纯文本消息。
     *
     * @param image 图片地址或 Base64 数据及可选文字
     * @return 图片消息 ID，图片数据无效、下载、上传或发送失败时返回 {@code null}
     */
    String sendMessage(ImageComponent image);
    String sendFile(Path file);
    boolean recall();
    boolean recall(String messageId);
}
