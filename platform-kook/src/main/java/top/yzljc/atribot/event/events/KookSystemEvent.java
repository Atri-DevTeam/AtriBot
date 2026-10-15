package top.yzljc.atribot.event.events;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName KookSystemEvent
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@RequiredArgsConstructor
public final class KookSystemEvent extends Event {
    private final String type;
    private final JsonNode raw;
}

