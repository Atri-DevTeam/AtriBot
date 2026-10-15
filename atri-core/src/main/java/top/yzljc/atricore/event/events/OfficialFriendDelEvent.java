package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code FRIEND_DEL}，即用户删除机器人好友时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialFriendDelEvent
 * @Created_at 2026/05/30
 * @Project AtriBot
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialFriendDelEvent extends Event {
    private final String userOpenId;
    private final String timestamp;
}