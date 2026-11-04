package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.card.Card;
import top.yzljc.atricore.chat.official.RT;
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
     * 使用成员加入事件 ID，向当前群发送被动卡片回复
     *
     * @param card 卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws top.yzljc.atricore.chat.official.QQMessageSendException QQ 开放平台拒绝发送消息
     */
    public String sendMessage(Card card) {
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), card);
    }

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
     * 使用成员加入事件 ID，向当前群发送图片回复
     *
     * @param image 图片组件
     * @return 平台返回的消息 ID，上传或发送失败时返回 null
     */
    public String sendMessage(ImageComponent image) {
        return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), image);
    }

    /**
     * 被动消息回复 - Markdown 消息，不自动 @ 新成员
     *
     * @param markdown Markdown 消息内容
     * @return 平台返回的消息 ID
     */
    public String sendMessage(Markdown markdown) {
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown);
    }

    /**
     * 被动消息回复 - Markdown 消息，可控制 @ 新成员
     *
     * @param markdown Markdown 消息内容
     * @param at       是否 @ 新成员
     * @return 平台返回的消息 ID
     */
    public String sendMessage(Markdown markdown, boolean at) {
        if (!at) {
            return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown);
        }
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), memberOpenId, markdown);
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
