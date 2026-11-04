package top.yzljc.atricore.event.events;

import top.yzljc.atricore.chat.official.button.ActionButton;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.card.Card;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.event.impl.FriendAddScene;

/**
 * 收到 {@code FRIEND_ADD}，即用户添加机器人好友时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialFriendAddEvent
 * @Created_at 2026/05/30
 * @Project AtriBot
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialFriendAddEvent extends Event {
    private final String eventId;
    private final String userOpenId;
    /**
     * QQ 开放平台绑定的互联 ID，取自事件的 {@code author.union_openid}，未携带时为 {@code null}
     */
    private final String unionOpenId;
    private final String timestamp;
    private final FriendAddScene scene;
    private final String sceneParam;
    private final String shortCode;

    /**
     * 使用好友添加事件 ID，向当前用户发送卡片开场白
     *
     * @param card 卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws top.yzljc.atricore.chat.official.QQMessageSendException QQ 开放平台拒绝发送消息
     */
    public String sendOpeningMessage(Card card) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), card);
    }

    /**
     * 被动消息好友开场白消息 - 纯文本
     *
     * @param message 开场白文本消息
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(String message) {
        return sendOpeningMessage(message, null);
    }

    /**
     * 被动消息好友开场白消息 - 纯文本，附带消息操作栏
     *
     * @param message      开场白文本消息
     * @param actionButton 消息操作栏，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(String message, ActionButton actionButton) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), message, actionButton);
    }

    /**
     * 被动消息好友开场白消息 - 图片消息
     *
     * @param image 开场白图片对象
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(ImageComponent image) {
        return sendOpeningMessage(image, null);
    }

    /**
     * 被动消息好友开场白消息 - 图片消息，附带消息操作栏
     *
     * @param image        开场白图片对象
     * @param actionButton 消息操作栏，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(ImageComponent image, ActionButton actionButton) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), image, actionButton);
    }

    /**
     * 被动消息好友开场白消息 - Markdown 消息 + 按钮
     *
     * @param markdown 开场白 Markdown 消息
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(Markdown markdown) {
        return sendOpeningMessage(markdown, null);
    }

    /**
     * 被动消息好友开场白消息 - Markdown 消息 + 按钮，附带消息操作栏
     *
     * @param markdown     开场白 Markdown 消息
     * @param actionButton 消息操作栏，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(Markdown markdown, ActionButton actionButton) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), markdown, actionButton);
    }
}