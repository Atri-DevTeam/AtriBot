package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.event.Event;

/**
 * QQ 交互事件的抽象基类，不单独分发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialInteractionEvents
 * @Created_at 2026/07/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public abstract class OfficialInteractionEvents extends Event {
    protected final String applicationId;
    protected final String eventId;
    protected final String id;
    protected final String scene;
    protected final String timestamp;
    protected final int type;
    protected final int version;
}