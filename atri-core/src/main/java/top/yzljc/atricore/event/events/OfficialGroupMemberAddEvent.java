package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code GROUP_MEMBER_ADD}，即群成员加入时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMemberAddEvent
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialGroupMemberAddEvent extends Event {
    private final String eventId;
    private final String groupOpenId;
    private final String memberOpenId;
    private final String timestamp;

    /**
     * 被动消息回复 - 纯文本消息
     *
     * @param text 文本消息内容
     * @return 平台返回的消息 ID
     */
    public String sendMessage(String text) {
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), text);
    }

    /**
     * 被动消息回复 - Markdown 消息
     * <p>被动消息的 Markdown内容会自动携带一行 {@code <@UserOpenId>} 且独占一行，如需关闭请用 {@link OfficialGroupAtMessageCreateEvent#sendMessage(Markdown, boolean)}</p>
     *
     * @param markdown Markdown 消息内容
     * @return 平台返回的消息 ID
     */
    public String sendMessage(Markdown markdown) {
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown);
    }

    /**
     * 被动消息回复 - Markdown 消息 + 按钮
     * <p>被动消息的 Markdown内容会自动携带一行 {@code <@UserOpenId>} 且独占一行，如需关闭请用 {@link OfficialGroupAtMessageCreateEvent#sendMessage(Markdown, boolean)}</p>
     *
     * @param markdown Markdown 消息内容
     * @param buttons  按钮对象
     * @return 平台返回的消息 ID
     * @deprecated 请使用 {@link Markdown#setKeyboard(Keyboard)} 设置按钮，旧的参数形式已弃用
     */
    @Deprecated
    public String sendMessage(Markdown markdown, Object buttons) {
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown, buttons);
    }

    /**
     * 被动消息回复 - Markdown 消息（关闭自动 @）
     *
     * @param markdown Markdown 消息内容
     * @param at       是否自动 @ 消息发送者
     * @return 平台返回的消息 ID
     */
    public String sendMessage(Markdown markdown, boolean at) {
        if (!at) {
            return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown);
        }
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), memberOpenId, markdown);
    }

    /**
     * 被动消息回复 - Markdown 消息 + 按钮（关闭自动 @）
     *
     * @param markdown Markdown 消息内容
     * @param buttons  按钮对象
     * @param at       是否自动 @ 消息发送者
     * @return 平台返回的消息 ID
     * @deprecated 请使用 {@link Markdown#setKeyboard(Keyboard)} 设置按钮，旧的参数形式已弃用
     */
    @Deprecated
    public String sendMessage(Markdown markdown, Object buttons, boolean at) {
        if (!at) {
            return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown, buttons);
        }
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), memberOpenId, markdown, buttons);
    }

    /**
     * 获取该成员的统一身份认证角色
     *
     * @return 该成员的统一身份认证角色
     */
    public UnifiedRole getUserBotRole() {
        return AtriProvider.getInstance().getAccessService().getRole(this.memberOpenId);
    }
}