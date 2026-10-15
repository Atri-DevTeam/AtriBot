package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.event.impl.FriendAddScene;

/**
 * 收到 {@code FRIEND_ADD}，即用户添加机器人好友时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialFriendAddEvent
 * @Created_at 2026/05/30
 * @Project AtriBot
 * @Package top.yzljc.atricore.event.events
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