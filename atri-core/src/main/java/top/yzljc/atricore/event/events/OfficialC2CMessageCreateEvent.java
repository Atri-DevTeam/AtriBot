package top.yzljc.atricore.event.events;

import lombok.Getter;
import lombok.Setter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.qq.QQUser;
import top.yzljc.atricore.platform.qq.QQMessage;

import java.util.List;

/**
 * 收到私聊消息 {@code C2C_MESSAGE_CREATE} 时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialC2CMessageCreateEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
public class OfficialC2CMessageCreateEvent extends Event {
    private final QQUser user;
    private final QQMessage message;
    private final String timestamp;
    /**
     * 消息场景中的开关按钮状态，消息未携带时为 {@code null}
     */
    @Setter
    private SwitchButtons switchButtons;

    public OfficialC2CMessageCreateEvent(QQUser user, QQMessage message, String timestamp) {
        this.user = user;
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * 被动消息回复 - 纯文本消息
     *
     * @param content 文本消息内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     */
    public String sendMessage(String content) {
        return this.user.sendMessage(this.message.getMessageId(), content);
    }

    /**
     * 被动消息回复 - Markdown 消息
     * <p>按钮请用 {@link Markdown#setKeyboard(Keyboard)} 设置。</p>
     *
     * @param markdown Markdown 消息内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     */
    public String sendMessage(Markdown markdown) {
        return this.user.sendMessage(this.message.getMessageId(), markdown);
    }

    /**
     * 被动消息回复 - 图片消息
     *
     * @param image 图片组件
     * @return 平台返回的消息 ID，上传或发送失败时返回 {@code null}
     */
    public String sendMessage(ImageComponent image) {
        return this.user.sendMessage(this.message.getMessageId(), image);
    }

    /**
     * 被动消息回复 - Markdown 消息 + 自定义键盘（已弃用）
     *
     * @param markdown Markdown 消息内容
     * @param keyboard 自定义键盘内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @deprecated 请用 {@link Markdown#setKeyboard(Keyboard)} 设置键盘，然后使用 {@link #sendMessage(Markdown)} 发送
     */
    @Deprecated
    public String sendMessage(Markdown markdown, Object keyboard) {
        return this.user.sendMessage(this.message.getMessageId(), markdown, keyboard);
    }

    /**
     * 被动消息回复 - Markdown 流式消息
     * <p>按顺序累加各增量的文本内容进行流式发送，仅使用 {@link Markdown#getText()}，不携带键盘等其他字段。</p>
     *
     * @param markdownDeltas Markdown 文本增量列表
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     */
    public String sendStreamMarkdownMessageD(List<Markdown> markdownDeltas) {
        return C2CChat.replyStreamDeltas(this.user.getUserId(), RT.message(this.message.getMessageId()), markdownDeltas);
    }

    /**
     * 被动消息回复 - 纯文本流式消息
     * <p>按顺序累加各文本增量进行流式发送。</p>
     *
     * @param textDeltas 文本增量列表
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     */
    public String sendStreamTextMessageD(List<String> textDeltas) {
        return C2CChat.replyTextStreamDeltas(this.user.getUserId(), RT.message(this.message.getMessageId()), textDeltas);
    }

    /**
     * 判断是否应忽略当前消息
     *
     * @return 消息发送者被屏蔽或被忽略时返回 {@code true}
     */
    public boolean shouldIgnore() {
        return this.user.isBlocked();
    }

    /**
     * 消息场景中的私聊菜单开关按钮状态集合
     *
     * @param switchButtons 开关按钮列表
     */
    public record SwitchButtons(List<SwitchButton> switchButtons) {
        /**
         * 单个开关按钮的状态
         *
         * @param k 开关名称
         * @param v 是否启用
         */
        public record SwitchButton(String k, boolean v) {
        }

        /**
         * 查询指定开关是否启用
         *
         * @param k 开关名称
         * @return 第一个同名开关的状态，未找到时返回 {@code false}
         */
        public boolean isEnabled(String k) {
            for (SwitchButton button : switchButtons) {
                if (button.k().equals(k)) {
                    return button.v();
                }
            }
            return false;
        }
    }
}
