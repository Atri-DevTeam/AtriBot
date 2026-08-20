package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupJoinEvent
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
@AllArgsConstructor
public class OfficialGroupAddRobotEvent extends Event {
    private final String eventId;
    private final String groupOpenId;
    private final String opMemberOpenId;
    private final String timestamp;

    public String sendOpeningMessage(String text) {
        return GroupChat.replyEventMessage(this.groupOpenId, this.eventId, text);
    }

    public String sendOpeningMessage(Markdown markdown) {
        return GroupChat.replyEventMessage(this.groupOpenId, this.eventId, markdown);
    }

    public String sendOpeningMessage(Markdown markdown, Object keyboard) {
        return GroupChat.replyEventMessage(this.groupOpenId, this.eventId, markdown, keyboard);
    }
}