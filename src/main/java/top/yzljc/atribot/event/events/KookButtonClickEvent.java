package top.yzljc.atribot.event.events;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName KookButtonClickEvent
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@RequiredArgsConstructor
public final class KookButtonClickEvent extends Event {
    private final JsonNode raw;

    public String getUserId() { return raw.path("extra").path("body").path("user_id").asText(); }
    public String getMessageId() { return raw.path("extra").path("body").path("msg_id").asText(); }
    public String getTargetId() { return raw.path("extra").path("body").path("target_id").asText(); }
    public String getValue() { return raw.path("extra").path("body").path("value").asText(); }
}

