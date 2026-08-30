package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.event.Event;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    void eachInstanceOwnsItsRuntimeStateWithoutGroupRouting() {
        GroupBotClient first = client("first", "10001", "/qq/groups/first/webhook");
        GroupBotClient second = client("second", "10002", "/qq/groups/second/webhook");

        assertNotSame(first.getTokenManager(), second.getTokenManager());
        assertNotSame(first.getChat(), second.getChat());
        assertNotSame(first.getEventDispatcher(), second.getEventDispatcher());
        assertEquals("https://api.sgroup.qq.com", first.getChat().getApiBaseUrl());
        assertEquals(first.getChat().getApiBaseUrl(), second.getChat().getApiBaseUrl());
    }

    @Test
    void messageEventsStayInsideTheirOwningClient() throws Exception {
        GroupBotClient first = client("first", "10001", "/qq/groups/first/webhook");
        GroupBotClient second = client("second", "10002", "/qq/groups/second/webhook");
        AtomicReference<GroupBotMessageEvent> firstEvent = new AtomicReference<>();
        AtomicInteger secondCalls = new AtomicInteger();
        first.addEventListener(new GroupBotEventListener() {
            @Override
            public void onGroupMessage(GroupBotMessageEvent event) {
                firstEvent.set(event);
            }
        });
        second.addEventListener(new GroupBotEventListener() {
            @Override
            public void onGroupMessage(GroupBotMessageEvent event) {
                secondCalls.incrementAndGet();
            }
        });

        JsonNode eventData = OBJECT_MAPPER.readTree("""
                {
                  "id": "message-1",
                  "content": "/help",
                  "group_openid": "group-shared-value",
                  "timestamp": "2026-08-29T10:00:00+08:00",
                  "author": {
                    "bot": false,
                    "username": "tester",
                    "member_openid": "member-1"
                  },
                  "mentions": [{"is_you": true}]
                }
                """);

        first.getEventDispatcher().dispatch("GROUP_MESSAGE_CREATE", "event-1", eventData);

        assertNotNull(firstEvent.get());
        assertSame(first, firstEvent.get().client());
        assertEquals("group-shared-value", firstEvent.get().groupOpenId());
        assertTrue(firstEvent.get().atBot());
        assertEquals(0, secondCalls.get());
        assertFalse(Event.class.isAssignableFrom(GroupBotMessageEvent.class));
    }

    @Test
    void dispatcherAcceptsOnlyMessagesAndGroupButtonClicks() {
        GroupBotClient client = client("secondary", "10002", "/qq/groups/secondary/webhook");
        GroupEventDispatcher dispatcher = client.getEventDispatcher();
        JsonNode groupMessage = OBJECT_MAPPER.createObjectNode()
                .put("group_openid", "group-secondary");
        ObjectNode button = OBJECT_MAPPER.createObjectNode()
                .put("group_openid", "group-secondary")
                .put("chat_type", 1);
        button.set("data", OBJECT_MAPPER.createObjectNode().put("type", 11));
        ObjectNode privateButton = button.deepCopy().put("chat_type", 2);

        assertTrue(dispatcher.accepts("GROUP_MESSAGE_CREATE", groupMessage));
        assertTrue(dispatcher.accepts("GROUP_AT_MESSAGE_CREATE", groupMessage));
        assertTrue(dispatcher.accepts("INTERACTION_CREATE", button));
        assertFalse(dispatcher.accepts("INTERACTION_CREATE", privateButton));
        assertFalse(dispatcher.accepts("C2C_MESSAGE_CREATE", groupMessage));
        assertFalse(dispatcher.accepts("GROUP_MEMBER_ADD", groupMessage));
        assertFalse(dispatcher.accepts("GROUP_JOIN_REQUEST", groupMessage));
    }

    @Test
    void chatUsesOneApiBaseAndItsOwnTokenForMarkdownButtons() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        GroupBotChat chat = new GroupBotChat(
                "https://api.sgroup.qq.com/", () -> "secondary-token", transport);
        Object keyboard = Map.of("content", Map.of("rows", List.of()));

        String messageId = chat.sendMessage(
                "group-1", new Markdown("hello"), keyboard).join();

        assertEquals("sent-1", messageId);
        assertEquals("https://api.sgroup.qq.com/v2/groups/group-1/messages", transport.lastUrl.get());
        assertEquals("secondary-token", transport.lastToken.get());
        JsonNode sent = OBJECT_MAPPER.readTree(transport.postBodies.getFirst());
        assertEquals("hello", sent.path("markdown").path("content").asText());
        assertTrue(sent.has("keyboard"));
    }

    @Test
    void replySequenceAndRecallAreInstanceLocalAndDatabaseFree() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        GroupBotChat chat = new GroupBotChat(
                "https://api.sgroup.qq.com", () -> "secondary-token", transport);

        chat.replyMessage("group-1", "incoming-1", "first").join();
        chat.replyMessage("group-1", "incoming-1", "second").join();

        JsonNode first = OBJECT_MAPPER.readTree(transport.postBodies.get(0));
        JsonNode second = OBJECT_MAPPER.readTree(transport.postBodies.get(1));
        assertEquals(1, first.path("msg_seq").asInt());
        assertEquals(2, second.path("msg_seq").asInt());
        assertTrue(chat.recallMessage("group-1", "sent-1"));
        assertEquals(
                "https://api.sgroup.qq.com/v2/groups/group-1/messages/sent-1",
                transport.lastDeleteUrl.get());
        assertEquals("secondary-token", transport.lastToken.get());
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
                "/qq/groups/secondary/webhook"
        );

        assertThrows(IllegalArgumentException.class, config::validateEnabled);
    }

    private GroupBotClient client(String key, String appId, String webhookPath) {
        GroupBotClient client = new GroupBotClient(new GroupBotConfig(
                key,
                true,
                appId,
                "test-secret-" + key,
                webhookPath
        ), "https://api.sgroup.qq.com");
        clients.add(client);
        return client;
    }

    private static final class RecordingTransport implements GroupBotTransport {
        private final List<String> postBodies = new ArrayList<>();
        private final AtomicReference<String> lastUrl = new AtomicReference<>();
        private final AtomicReference<String> lastDeleteUrl = new AtomicReference<>();
        private final AtomicReference<String> lastToken = new AtomicReference<>();

        @Override
        public Response post(String url, String json, String accessToken) {
            lastUrl.set(url);
            lastToken.set(accessToken);
            postBodies.add(json);
            return new Response(200, "{\"id\":\"sent-1\"}");
        }

        @Override
        public Response delete(String url, String accessToken) {
            lastDeleteUrl.set(url);
            lastToken.set(accessToken);
            return new Response(204, "");
        }
    }
}
