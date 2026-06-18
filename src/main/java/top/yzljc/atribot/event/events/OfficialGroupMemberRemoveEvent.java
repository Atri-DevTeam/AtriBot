package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMemberRemoveEvent
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialGroupMemberRemoveEvent extends Event {
    private final String groupOpenId;
    private final String memberOpenId;
    private final String timestamp;
}