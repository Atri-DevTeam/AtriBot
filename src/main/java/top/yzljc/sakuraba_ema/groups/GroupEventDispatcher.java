package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.platform.qq.WebSocketClient;

import java.util.Set;

/**
 * Whitelists group-domain events before reusing AtriBot's existing event parser.
 */
@Slf4j
public final class GroupEventDispatcher {

    private static final Set<String> GROUP_EVENT_TYPES = Set.of(
            "GROUP_AT_MESSAGE_CREATE",
            "GROUP_MESSAGE_CREATE",
            "GROUP_ADD_ROBOT",
            "GROUP_DEL_ROBOT",
            "GROUP_MEMBER_ADD",
            "GROUP_MEMBER_REMOVE",
            "GROUP_JOIN_REQUEST",
            "INTERACTION_CREATE"
    );

    private final GroupBotClient client;
    private final EventSink eventSink;

    GroupEventDispatcher(GroupBotClient client) {
        this(client, WebSocketClient::dispatchEvent);
    }

    GroupEventDispatcher(GroupBotClient client, EventSink eventSink) {
        this.client = client;
        this.eventSink = eventSink;
    }

    public boolean accepts(String eventType, JsonNode eventData) {
        if (eventType == null || !GROUP_EVENT_TYPES.contains(eventType)
                || eventData == null || eventData.isNull()) {
            return false;
        }
        if ("INTERACTION_CREATE".equals(eventType) && eventData.path("chat_type").asInt(-1) != 1) {
            return false;
        }
        return !groupOpenId(eventData).isBlank();
    }

    public void dispatch(String eventType, String eventId, JsonNode eventData) {
        if (!accepts(eventType, eventData)) {
            log.debug("实例 {} 忽略非群聊事件: {}", client.key(), eventType);
            return;
        }

        String groupOpenId = groupOpenId(eventData);
        GroupBotRegistry.remember(groupOpenId, client);
        GroupBotRouteStore.remember(groupOpenId, client);
        eventSink.dispatch(eventType, eventId, eventData);
    }

    static String groupOpenId(JsonNode eventData) {
        return eventData == null ? "" : eventData.path("group_openid").asText("").trim();
    }

    @FunctionalInterface
    interface EventSink {
        void dispatch(String eventType, String eventId, JsonNode eventData);
    }
}
