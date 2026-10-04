package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.RT;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.event.impl.FriendAddScene;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialFriendAddEvent
 * @Created_at 2026/05/30
 * @Project AtriBot
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public class OfficialFriendAddEvent extends Event {
    private final String eventId;
    private final String userOpenId;
    private final String timestamp;
    private final FriendAddScene scene;
    private final String sceneParam;
    private final String shortCode;

    public String sendOpeningMessage(String message) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), message);
    }

    public String sendOpeningMessage(Markdown markdown) {
        return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), markdown);
    }
}