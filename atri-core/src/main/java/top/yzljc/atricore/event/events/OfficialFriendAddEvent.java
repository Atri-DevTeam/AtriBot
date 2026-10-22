package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.button.Keyboard;
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
     * 被动消息好友开场白消息 - 纯文本
     *
     * @param message 开场白文本消息
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(String message) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), message);
    }

    /**
     * 被动消息好友开场白消息 - Markdown 消息（按钮请用 {@link Markdown#setKeyboard(Keyboard)} 设置，旧的参数形式已弃用）
     *
     * @param markdown 开场白 Markdown 消息
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(Markdown markdown) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), markdown);
    }

    /**
     * 被动消息好友开场白消息 - 图片消息
     *
     * @param image 开场白图片对象
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(ImageComponent image) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), image);
    }

    /**
     * 被动消息好友开场白消息 - Markdown 消息 + 按钮
     *
     * @param markdown 开场白 Markdown 消息
     * @param keyboard 按钮键盘对象
     * @return 平台返回的消息 ID
     * @deprecated 请使用 {@link OfficialFriendAddEvent#sendOpeningMessage(Markdown)} 发送
     */
    @Deprecated
    public String sendOpeningMessage(Markdown markdown, Object keyboard) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), markdown, keyboard);
    }
}