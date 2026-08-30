package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Parses only group messages and group button callbacks for one bot instance.
 * No event is forwarded to AtriBot's primary EventManager.
 */
@Slf4j
public final class GroupEventDispatcher {

    private static final Set<String> MESSAGE_EVENT_TYPES = Set.of(
            "GROUP_AT_MESSAGE_CREATE",
            "GROUP_MESSAGE_CREATE"
    );
    private static final String INTERACTION_EVENT_TYPE = "INTERACTION_CREATE";
    private static final int GROUP_CHAT_TYPE = 1;
    private static final int BUTTON_CLICK_TYPE = 11;

    private final GroupBotClient client;

    GroupEventDispatcher(GroupBotClient client) {
        this.client = client;
    }

    public boolean accepts(String eventType, JsonNode eventData) {
        if (eventType == null || eventData == null || eventData.isMissingNode() || eventData.isNull()) {
            return false;
        }
        if (groupOpenId(eventData).isBlank()) {
            return false;
        }
        if (MESSAGE_EVENT_TYPES.contains(eventType)) {
            return true;
        }
        return INTERACTION_EVENT_TYPE.equals(eventType)
                && eventData.path("chat_type").asInt(-1) == GROUP_CHAT_TYPE
                && eventData.path("data").path("type").asInt(-1) == BUTTON_CLICK_TYPE;
    }

    public void dispatch(String eventType, String eventId, JsonNode eventData) {
        if (!accepts(eventType, eventData)) {
            log.debug("QQ 群聊 Bot 实例 {} 忽略事件: {}", client.key(), eventType);
            return;
        }
        if (MESSAGE_EVENT_TYPES.contains(eventType)) {
            client.publish(parseMessage(eventType, eventId, eventData));
            return;
        }
        client.publish(parseButton(eventId, eventData));
    }

    private GroupBotMessageEvent parseMessage(String eventType, String eventId, JsonNode data) {
        JsonNode author = data.path("author");
        return new GroupBotMessageEvent(
                client,
                eventType,
                eventId,
                groupOpenId(data),
                data.path("id").asText(null),
                data.path("content").asText(""),
                author.path("member_openid").asText(null),
                author.path("username").asText(null),
                data.path("timestamp").asText(null),
                author.path("bot").asBoolean(false),
                "GROUP_AT_MESSAGE_CREATE".equals(eventType) || mentionsThisBot(data.path("mentions")),
                data
        );
    }

    private GroupBotButtonInteractionEvent parseButton(String eventId, JsonNode data) {
        JsonNode resolved = data.path("data").path("resolved");
        return new GroupBotButtonInteractionEvent(
                client,
                eventId,
                data.path("id").asText(null),
                groupOpenId(data),
                data.path("group_member_openid").asText(null),
                resolved.path("button_id").asText(null),
                resolved.path("button_data").asText(null),
                data.path("timestamp").asText(null),
                data
        );
    }

    private static boolean mentionsThisBot(JsonNode mentions) {
        if (!mentions.isArray()) {
            return false;
        }
        for (JsonNode mention : mentions) {
            if (mention.path("is_you").asBoolean(false)) {
                return true;
            }
        }
        return false;
    }

    static String groupOpenId(JsonNode eventData) {
        return eventData == null ? "" : eventData.path("group_openid").asText("").trim();
    }
}
