package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.event.Event;

/**
 * C2C 普通消息或流式消息发送失败时，按各自发送入口的条件触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialC2CSendFailEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialC2CSendFailEvent extends Event {
    private final String userId;
    private final int errorCode;
    private final String errorMessage;
}