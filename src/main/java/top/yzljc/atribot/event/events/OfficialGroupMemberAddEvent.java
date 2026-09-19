package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.RT;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMemberAddEvent
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
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
        return OfficialUsers.getRole(this.memberOpenId);
    }
}