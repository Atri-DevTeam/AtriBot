package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code GROUP_MEMBER_REMOVE}，即群成员离开时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMemberRemoveEvent
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialGroupMemberRemoveEvent extends Event {
    private final String eventId;
    private final String groupOpenId;
    private final String memberOpenId;
    private final String timestamp;

    public UnifiedRole getUserBotRole() {
        return AtriProvider.getInstance().getAccessService().getRole(this.memberOpenId);
    }
}