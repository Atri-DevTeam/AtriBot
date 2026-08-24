package top.yzljc.atribot.platform.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordManagerReconnectTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final URI PRIMARY_GATEWAY = URI.create("wss://gateway.discord.test");
    private static final URI RESUME_GATEWAY = URI.create("wss://resume.discord.test");

    @Test
    void failedResumeFallsBackToPrimaryGatewayAndClearsSession() throws Exception {
        Harness harness = new Harness(PRIMARY_GATEWAY);
        RecordingClient initial = harness.connectInitial();
        markReady(harness.manager, initial);

        harness.manager.onClose(initial, -1, "connection lost", false);
        harness.scheduler.runNext();

        RecordingClient resume = harness.latestClient();
        assertEquals(normalized(RESUME_GATEWAY), resume.getURI());
        assertTrue(harness.manager.canResume());

        harness.manager.onClose(resume, -1, "Connect timed out", false);

        assertFalse(harness.manager.canResume());
        harness.scheduler.runNext();

        RecordingClient fallback = harness.latestClient();
        assertEquals(normalized(PRIMARY_GATEWAY), fallback.getURI());
        assertEquals(1, harness.gatewayFetches.get());
    }

    @Test
    void invalidSequenceAndTimedOutSessionStartFreshSessions() throws Exception {
        for (int closeCode : new int[]{4007, 4009}) {
            Harness harness = new Harness(PRIMARY_GATEWAY);
            RecordingClient initial = harness.connectInitial();
            markReady(harness.manager, initial);

            harness.manager.onClose(initial, closeCode, "session is not resumable", true);

            assertFalse(harness.manager.canResume(), "close code " + closeCode + " must clear the session");
            harness.scheduler.runNext();
            assertEquals(normalized(PRIMARY_GATEWAY), harness.latestClient().getURI());
        }
    }

    @Test
    void repeatedPrimaryGatewayFailuresRefreshGatewayUrl() throws Exception {
        URI refreshedGateway = URI.create("wss://gateway-refreshed.discord.test");
        Harness harness = new Harness(PRIMARY_GATEWAY, refreshedGateway);
        RecordingClient current = harness.connectInitial();

        for (int attempt = 0; attempt < 3; attempt++) {
            harness.manager.onClose(current, -1, "Connect timed out", false);
            harness.scheduler.runNext();
            current = harness.latestClient();
        }

        assertEquals(2, harness.gatewayFetches.get());
        assertEquals(normalized(refreshedGateway), current.getURI());
    }

    @Test
    void heartbeatTimeoutUsesResumableCloseCode() throws Exception {
        Harness harness = new Harness(PRIMARY_GATEWAY);
        RecordingClient initial = harness.connectInitial();
        markReady(harness.manager, initial);

        initial.handleHeartbeatTimeout();

        assertEquals(DiscordWebSocketClient.HEARTBEAT_TIMEOUT_CLOSE_CODE, initial.closeCode);
        assertNotEquals(1000, initial.closeCode);
        assertNotEquals(1001, initial.closeCode);
        assertEquals(1, harness.scheduler.size());
    }

    @Test
    void staleCloseCallbackCannotDisruptReplacementConnection() throws Exception {
        Harness harness = new Harness(PRIMARY_GATEWAY);
        RecordingClient initial = harness.connectInitial();
        markReady(harness.manager, initial);

        harness.manager.onClose(initial, -1, "connection lost", false);
        harness.scheduler.runNext();
        RecordingClient replacement = harness.latestClient();

        harness.manager.onClose(initial, -1, "late close callback", false);

        assertTrue(harness.manager.isCurrentClient(replacement));
        assertEquals(0, harness.scheduler.size());
    }

    private static void markReady(DiscordManager manager, RecordingClient client) {
        ObjectNode ready = OBJECT_MAPPER.createObjectNode();
        ready.put("session_id", "session-1");
        ready.put("resume_gateway_url", RESUME_GATEWAY.toString());
        manager.onDispatch(client, "READY", ready, 42);
    }

    private static URI normalized(URI uri) {
        return URI.create(uri + "?v=10&encoding=json");
    }

    private static final class Harness {
        private final List<RecordingClient> clients = new ArrayList<>();
        private final RecordingScheduler scheduler = new RecordingScheduler();
        private final AtomicInteger gatewayFetches = new AtomicInteger();
        private final URI[] gatewayUrls;
        private final DiscordManager manager;

        private Harness(URI... gatewayUrls) {
            this.gatewayUrls = gatewayUrls;
            this.manager = new DiscordManager(
                    "https://discord.test/api/v10",
                    "token",
                    0,
                    0,
                    1,
                    (uri, owner, token, intents, shardId, shardCount) -> {
                        RecordingClient client = new RecordingClient(uri, owner, token, intents, shardId, shardCount);
                        clients.add(client);
                        return client;
                    },
                    scheduler,
                    this::nextGatewayUrl
            );
        }

        private URI nextGatewayUrl() {
            int fetch = gatewayFetches.getAndIncrement();
            return gatewayUrls[Math.min(fetch, gatewayUrls.length - 1)];
        }

        private RecordingClient connectInitial() throws Exception {
            manager.reconnect(DiscordManager.ConnectionMode.IDENTIFY);
            return latestClient();
        }

        private RecordingClient latestClient() {
            return clients.getLast();
        }
    }

    private static final class RecordingScheduler implements DiscordManager.ReconnectScheduler {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void schedule(Runnable task, long delay, TimeUnit unit) {
            tasks.add(task);
        }

        private void runNext() {
            Runnable task = tasks.poll();
            if (task == null) {
                throw new AssertionError("Expected a scheduled reconnect");
            }
            task.run();
        }

        private int size() {
            return tasks.size();
        }
    }

    private static final class RecordingClient extends DiscordWebSocketClient {
        private int closeCode = Integer.MIN_VALUE;

        private RecordingClient(
                URI serverUri,
                DiscordManager manager,
                String botToken,
                int intents,
                int shardId,
                int shardCount
        ) {
            super(serverUri, manager, botToken, intents, shardId, shardCount);
        }

        @Override
        public void connect() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void close(int code, String message) {
            closeCode = code;
        }
    }
}
