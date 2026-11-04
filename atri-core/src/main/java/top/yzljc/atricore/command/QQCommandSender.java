package top.yzljc.atricore.command;

import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.button.ActionButton;
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

    String sendMessage(Markdown markdown, boolean at);

    String sendMessage(ImageComponent image);

    /**
     * 使用当前指令消息作为回复来源，被动回复 QQ 官方群聊或单聊卡片
     *
     * @param card 卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws top.yzljc.atricore.platform.UnsupportedPlatform         当前发送者不属于 QQ 官方群聊或单聊
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
     * 被动回复QQ 官方群聊或单聊 Markdown，可同时引用当前消息
     *
     * @param markdown Markdown 回复内容
     * @param at       是否 @ 当前用户，仅群聊生效
     * @param ref      是否引用当前消息；只控制引用，不改变被动回复来源
     * @return 消息 ID，发送失败返回 null
     */
    String sendMessage(Markdown markdown, boolean at, boolean ref);

    String sendStreamTextMessage(List<String> textDeltas);

    String sendStreamMarkdownMessage(List<Markdown> markdownDeltas);

    boolean recall();

    boolean recall(String messageId);

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param text         文本内容
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    String sendMessage(String text, ActionButton actionButton);

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param text         文本内容
     * @param ref          是否引用当前消息
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    String sendMessage(String text, boolean ref, ActionButton actionButton);

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param markdown     Markdown 内容，可携带键盘
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    String sendMessage(Markdown markdown, ActionButton actionButton);

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param markdown     Markdown 内容，可携带键盘
     * @param at           是否自动 @ 用户，仅群聊生效
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    String sendMessage(Markdown markdown, boolean at, ActionButton actionButton);

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param image        图片组件
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    String sendMessage(ImageComponent image, ActionButton actionButton);

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param image        图片组件
     * @param ref          是否引用当前消息
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    String sendMessage(ImageComponent image, boolean ref, ActionButton actionButton);

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param markdown     Markdown 内容，可携带键盘
     * @param at           是否自动 @ 用户，仅群聊生效
     * @param ref          是否引用当前消息
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    String sendMessage(Markdown markdown, boolean at, boolean ref, ActionButton actionButton);

}
