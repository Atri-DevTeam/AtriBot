package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.event.Event;

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
    private final String unionOpenId;
    private final String timestamp;
}