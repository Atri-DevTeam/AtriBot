package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
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
    private final String userOpenId;
    private final String timestamp;
    private final FriendAddScene scene;
    private final String sceneParam;
    private final String shortCode;
}