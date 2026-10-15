package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
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

    public String sendMessage(Markdown markdown) {
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown);
    }

    public String sendMessage(Markdown markdown, Object buttons) {
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown, buttons);
    }

    public String sendMessage(Markdown markdown, boolean at) {
        if (!at) {
            return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown);
        }
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), memberOpenId, markdown);
    }

    public String sendMessage(Markdown markdown, Object buttons, boolean at) {
        if (!at) {
            return GroupChat.replyMessage(groupOpenId, RT.event(eventId), markdown, buttons);
        }
        return GroupChat.replyMessage(groupOpenId, RT.event(eventId), memberOpenId, markdown, buttons);
    }

    public UnifiedRole getUserBotRole() {
        return AtriProvider.getInstance().getAccessService().getRole(this.memberOpenId);
    }
}