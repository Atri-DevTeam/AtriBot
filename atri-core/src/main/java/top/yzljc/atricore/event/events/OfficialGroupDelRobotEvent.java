package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code GROUP_DEL_ROBOT}，即机器人被移出群聊时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupDelRobotEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialGroupDelRobotEvent extends Event {
    private final String groupOpenId;
    private final String opMemberOpenId;
    private final String timestamp;
}