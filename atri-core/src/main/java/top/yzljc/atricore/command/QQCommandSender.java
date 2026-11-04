package top.yzljc.atricore.command;

import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.card.Card;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.platform.PlatformRole;
import top.yzljc.atricore.platform.qq.QQMessage;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName QQCommandSender
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
public interface QQCommandSender extends CommandSender {

    Platform getPlatform();

    boolean isBot();

    String getGroupId();

    PlatformRole getRole();

    QQMessage getMessage();

    /**
     * 被动回复 QQ 官方群聊或单聊纯文本消息，可同时引用当前消息
     *
     * @param text 回复内容
     * @param ref  是否引用当前消息；只控制引用，不改变被动回复来源
     * @return 消息 ID，发送失败返回 null
     */
    String sendMessage(String text, boolean ref);

    String sendMessage(Markdown markdown);

    String sendMessage(Markdown markdown, Object buttons);

    String sendMessage(Markdown markdown, boolean at);

    String sendMessage(Markdown markdown, Object buttons, boolean at);

    String sendMessage(ImageComponent image);

    /**
     * 使用当前指令消息作为回复来源，被动回复 QQ 官方群聊或单聊卡片
     *
     * @param card 卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws top.yzljc.atricore.platform.UnsupportedPlatform 当前发送者不属于 QQ 官方群聊或单聊
     * @throws top.yzljc.atricore.chat.official.QQMessageSendException QQ 开放平台拒绝发送消息
     */
    String sendMessage(Card card);

    /**
     * 被动回复 QQ 官方群聊或单聊图片消息，可同时引用当前消息
     *
     * @param image 图片组件
     * @param ref   是否引用当前消息；只控制引用，不改变被动回复来源
     * @return 消息 ID，上传或发送失败返回 null
     */
    String sendMessage(ImageComponent image, boolean ref);

    /**
     * 被动回复 QQ 官方群聊或单聊 Markdown 消息，可同时引用当前消息
     *
     * @param markdown Markdown 回复内容
     * @param at       是否 @ 当前用户，仅群聊生效
     * @param ref      是否引用当前消息；只控制引用，不改变被动回复来源
     * @return 消息 ID，发送失败返回 null
     */
    String sendMessage(Markdown markdown, boolean at, boolean ref);

    /**
     * 被动回复带键盘的 QQ 官方群聊或单聊 Markdown，可同时引用当前消息
     *
     * @param markdown Markdown 回复内容
     * @param buttons  键盘按钮对象，无键盘时传入 null
     * @param at       是否 @ 当前用户，仅群聊生效
     * @param ref      是否引用当前消息；只控制引用，不改变被动回复来源
     * @return 消息 ID，发送失败返回 null
     */
    String sendMessage(Markdown markdown, Object buttons, boolean at, boolean ref);

    String sendStreamTextMessage(List<String> textDeltas);

    String sendStreamMarkdownMessage(List<Markdown> markdownDeltas);

    boolean recall();

    boolean recall(String messageId);
}
