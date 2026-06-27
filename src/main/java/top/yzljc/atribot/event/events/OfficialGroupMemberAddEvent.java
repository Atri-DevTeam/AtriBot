package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
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
        return GroupChat.replyEventMessage(groupOpenId, memberOpenId, eventId, markdown);
    }

    public String sendMessage(Markdown markdown, Object buttons) {
        return GroupChat.replyEventMessage(groupOpenId, memberOpenId, eventId, markdown, buttons);
    }
}