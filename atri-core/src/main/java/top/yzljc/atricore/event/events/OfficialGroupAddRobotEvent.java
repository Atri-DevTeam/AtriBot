package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code GROUP_ADD_ROBOT}，即机器人被加入群聊时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupAddRobotEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialGroupAddRobotEvent extends Event {
    private final String eventId;
    private final String groupOpenId;
    private final String opMemberOpenId;
    private final String timestamp;

    /**
     * 被动消息群开放白消息 - 纯文本
     *
     * @param text 开场白文本消息
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(String text) {
        return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), text);
    }

    /**
     * 被动消息群开放白消息 - Markdown 消息（按钮请用 {@link Markdown#setKeyboard(Keyboard)} 设置，旧的参数形式已弃用
     *
     * @param markdown 开场白 Markdown 消息
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(Markdown markdown) {
        return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), markdown);
    }

    /**
     * 被动消息群开场白消息 - 图片消息
     *
     * @param image 开场白图片对象
     * @return 平台返回的消息 ID
     */
    public String sendOpeningMessage(ImageComponent image) {
        return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), image);
    }

    /**
     * 被动消息群开场白消息 - Markdown 消息 + 按钮
     *
     * @param markdown 开场白 Markdown 消息
     * @param keyboard 按钮键盘对象
     * @return 平台返回的消息 ID
     * @deprecated 请使用 {@link OfficialGroupAddRobotEvent#sendOpeningMessage(Markdown)} 发送
     */
    @Deprecated
    public String sendOpeningMessage(Markdown markdown, Object keyboard) {
        return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), markdown, keyboard);
    }
}