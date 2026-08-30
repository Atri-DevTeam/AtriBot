package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.qq.BotEvents;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GroupBotSupportTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<GroupBotClient> clients = new ArrayList<>();

    @AfterEach
    void closeClients() {
        clients.forEach(GroupBotClient::close);
    }

    @Test
    void eachInstanceOwnsItsTokenAndChatState() {
        GroupBotClient first = client("first", "10001", "/qq/groups/first/webhook");
        GroupBotClient second = client("second", "10002", "/qq/groups/second/webhook");

        assertNotSame(first.getTokenManager(), second.getTokenManager());
        assertNotSame(first.getChatService(), second.getChatService());
        assertSame(first.getTokenManager(), first.getChatService().getTokenManager());
        assertSame(second.getTokenManager(), second.getChatService().getTokenManager());
    }

    @Test
    void dispatcherAcceptsOnlyGroupDomainEventsAndKeepsTheirInstanceRoute() {
        GroupBotClient client = client("secondary", "10002", "/qq/groups/secondary/webhook");
        AtomicInteger dispatched = new AtomicInteger();
        GroupEventDispatcher dispatcher = new GroupEventDispatcher(
                client,
                (eventType, eventId, eventData) -> dispatched.incrementAndGet()
        );
        JsonNode groupEvent = OBJECT_MAPPER.createObjectNode().put("group_openid", "group-secondary");
        JsonNode privateInteraction = OBJECT_MAPPER.createObjectNode()
                .put("group_openid", "group-secondary")
                .put("chat_type", 2);

        assertTrue(dispatcher.accepts("GROUP_MESSAGE_CREATE", groupEvent));
        assertFalse(dispatcher.accepts("C2C_MESSAGE_CREATE", groupEvent));
        assertFalse(dispatcher.accepts("AT_MESSAGE_CREATE", groupEvent));
        assertFalse(dispatcher.accepts("DIRECT_MESSAGE_CREATE", groupEvent));
        assertFalse(dispatcher.accepts("INTERACTION_CREATE", privateInteraction));

        dispatcher.dispatch("GROUP_MESSAGE_CREATE", "event-1", groupEvent);
        assertEquals(1, dispatched.get());
        assertSame(client, GroupBotRegistry.find("group-secondary").orElseThrow());

        dispatcher.dispatch("GROUP_DEL_ROBOT", "event-2", groupEvent);
        assertEquals(2, dispatched.get());
        assertSame(client, GroupBotRegistry.find("group-secondary").orElseThrow());
    }

    @Test
    void existingOfficialGroupEventParserIsReused() {
        GroupBotClient client = client("secondary", "10002", "/qq/groups/secondary/webhook");
        GroupBotRegistry.remember("group-secondary", client);
        AtomicReference<OfficialGroupMessageCreateEvent> received = new AtomicReference<>();

        class CaptureListener implements Listener {
            @EventHandler
            public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
                received.set(event);
            }
        }

        CaptureListener listener = new CaptureListener();
        EventManager.getInstance().registerEvents(listener);
        try {
            JsonNode eventData = OBJECT_MAPPER.readTree("""
                    {
                      "id": "message-1",
                      "content": "/help",
                      "group_openid": "group-secondary",
                      "timestamp": "2026-08-29T10:00:00+08:00",
                      "message_type": 0,
                      "author": {
                        "bot": false,
                        "username": "tester",
                        "member_openid": "member-1",
                        "member_role": "member"
                      },
                      "mentions": [],
                      "message_scene": {"ext": ["ref_idx=42"]}
                    }
                    """);

            BotEvents.handleGroupChatEvent(eventData);

            OfficialGroupMessageCreateEvent event = received.get();
            assertNotNull(event);
            assertEquals("group-secondary", event.getGroupId());
            assertEquals("member-1", event.getUser().getUserId());
            assertEquals(Platform.OFFICIAL_GROUP, event.getUser().getPlatform());
            assertEquals("42", event.getMessage().getRefIdx());
            assertSame(client, GroupBotRegistry.find(event.getGroupId()).orElseThrow());
        } catch (Exception e) {
            fail(e);
        } finally {
            EventManager.getInstance().unregisterEvents(listener);
        }
    }

    @Test
    void webhookSigningSeedIsInstanceLocalAndDeterministic() {
        byte[] seed = GroupWebhookHandler.deriveSeed("abc");

        assertEquals(32, seed.length);
        assertArrayEquals("abcabc".getBytes(StandardCharsets.UTF_8),
                new byte[]{seed[0], seed[1], seed[2], seed[3], seed[4], seed[5]});
    }

    @Test
    void enabledInstanceRejectsMissingCredentials() {
        GroupBotConfig config = new GroupBotConfig(
                "secondary",
                true,
                "",
                "",
                "https://api.sgroup.qq.com/",
                "/qq/groups/secondary/webhook"
        );

        assertEquals("https://api.sgroup.qq.com", config.apiBaseUrl());
        assertThrows(IllegalArgumentException.class, config::validateEnabled);
    }

    private GroupBotClient client(String key, String appId, String webhookPath) {
        GroupBotClient client = new GroupBotClient(new GroupBotConfig(
                key,
                true,
                appId,
                "test-secret-" + key,
                "https://sandbox.api.sgroup.qq.com",
                webhookPath
        ));
        clients.add(client);
        return client;
    }
}
