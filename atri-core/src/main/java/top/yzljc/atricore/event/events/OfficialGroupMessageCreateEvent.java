package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import lombok.Getter;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.qq.QQUser;
import top.yzljc.atricore.platform.qq.QQMessage;

/**
 * 收到全量群消息 {@code GROUP_MESSAGE_CREATE} 时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMessageCreateEvent
 * @Created_at 2026/05/22
 * @Project AtriBot
 * @Package top.yzljc.atricore.event.events
 * @Description 本事件中所有回复消息的方法均为被动
 */
@Getter
public class OfficialGroupMessageCreateEvent extends Event {
    private final QQUser user;
    private final String groupId;
    private final QQMessage message;
    private final String timestamp;
    private final boolean isAtBot;

    public OfficialGroupMessageCreateEvent(QQUser user, String groupId, QQMessage message, String timestamp, boolean isAtBot) {
        this.user = user;
        this.groupId = groupId;
        this.message = message;
        this.timestamp = timestamp;
        this.isAtBot = isAtBot;
    }

    /**
     * 被动消息回复 - 纯文本消息
     *
     * @param content 文本消息内容
     * @return 平台返回的消息 ID
     */
    public String sendMessage(String content) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), content);
    }

    /**
     * 被动消息回复 - Markdown 消息
     * <p>被动消息的 Markdown 内容会自动携带一行 {@code <@UserOpenId>} 且独占一行，如需关闭请用 {@link OfficialGroupMessageCreateEvent#sendMessage(Markdown, boolean)}</p>
     *
     * @param markdown Markdown 消息内容
     * @return 平台返回的消息 ID
     */
    public String sendMessage(Markdown markdown) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown);
    }

    /**
     * 被动消息回复 - Markdown 消息（可控制自动 @）
     *
     * @param markdown Markdown 消息内容
     * @param at       是否自动 @ 消息发送者
     * @return 平台返回的消息 ID
     */
    public String sendMessage(Markdown markdown, boolean at) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, at);
    }

    /**
     * 被动消息回复 - 图片消息
     *
     * @param image 图片组件
     * @return 平台返回的消息 ID
     */
    public String sendMessage(ImageComponent image) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), image);
    }

    /**
     * 被动消息回复 - Markdown 消息 + 自定义键盘（已弃用）
     * <p>被动消息的 Markdown 内容会自动携带一行 {@code <@UserOpenId>} 且独占一行，如需关闭请用 {@link OfficialGroupMessageCreateEvent#sendMessage(Markdown, Object, boolean)}</p>
     *
     * @param markdown Markdown 消息内容
     * @param buttons  自定义键盘内容
     * @return 平台返回的消息 ID
     * @deprecated 请使用 {@link OfficialGroupMessageCreateEvent#sendMessage(Markdown)} 中 {@link Markdown#setKeyboard(top.yzljc.atricore.chat.official.button.Keyboard)} 的方式设置键盘按钮
     */
    public String sendMessage(Markdown markdown, Object buttons) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, buttons);
    }

    /**
     * 被动消息回复 - Markdown 消息 + 自定义键盘（可控制自动 @，已弃用）
     *
     * @param markdown Markdown 消息内容
     * @param buttons  自定义键盘内容
     * @param at       是否自动 @ 消息发送者
     * @return 平台返回的消息 ID
     * @deprecated 请使用 {@link OfficialGroupMessageCreateEvent#sendMessage(Markdown, boolean)} 中 {@link Markdown#setKeyboard(top.yzljc.atricore.chat.official.button.Keyboard)} 的方式设置键盘按钮
     */
    public String sendMessage(Markdown markdown, Object buttons, boolean at) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, buttons, at);
    }

    /**
     * 判断是否应忽略当前消息
     *
     * @return 群在黑名单中或消息发送者被屏蔽时返回 {@code true}
     */
    public boolean shouldIgnore() {
        if (AtriProvider.getInstance().getAccessService().isGroupBlacklisted(this.groupId)) return true;
        return this.user.isBlocked();
    }

    /**
     * 判断 @ 机器人的消息去除开头的 @ 和首尾空白后是否为空
     *
     * @return 消息 @ 机器人且去除上述内容后为空时返回 {@code true}，未 @ 机器人时返回 {@code false}
     */
    public boolean isEmptyMessage() {
        if (isAtBot) {
            String replaced = this.message.getContent().replaceFirst("^<@[^>]+>\\s*", "").trim();
            return replaced.isBlank() || replaced.equals(" ");
        }
        return false;
    }
}
