package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialC2CPushFailEvent
 * @Created_at 2026/07/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialC2CPushFailEvent extends Event {
    private final String userId;
    private final int errorCode;
    private final String errorMessage;
}