package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
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

    public String sendOpeningMessage(String text) {
        return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), text);
    }

    public String sendOpeningMessage(Markdown markdown) {
        return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), markdown);
    }

    public String sendOpeningMessage(Markdown markdown, Object keyboard) {
        return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), markdown, keyboard);
    }
}