package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupDelEvent
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
@AllArgsConstructor
public class OfficialGroupDelEvent extends Event {
    private final String groupOpenId;
    private final String opMemberOpenId;
    private final String timestamp;
}