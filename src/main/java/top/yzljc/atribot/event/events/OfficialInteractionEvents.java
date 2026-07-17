package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialInteractionEvents
 * @Created_at 2026/07/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialInteractionEvents extends Event {
    protected final String applicationId;
    protected final String eventId;
    protected final String id;
    protected final String scene;
    protected final String timestamp;
    protected final int type;
    protected final int version;
}