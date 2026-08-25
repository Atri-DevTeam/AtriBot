package top.yzljc.atribot.platform.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.drafts.Draft_6455;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.DiscordMessageCreateEvent;
import top.yzljc.atribot.event.events.DiscordSlashCommandEvent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.service.runtime.ThreadManager;
import org.java_websocket.client.WebSocketClient;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class DiscordWebSocketClient extends WebSocketClient {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    static final int HEARTBEAT_TIMEOUT_CLOSE_CODE = 4000;
    private static final int DISCORD_HEARTBEAT = 1;
    private static final int DISCORD_IDENTIFY = 2;
    private static final int DISCORD_RESUME = 6;
    private static final int DISCORD_RECONNECT = 7;
    private static final int DISCORD_HELLO = 10;
    private static final int DISCORD_HEARTBEAT_ACK = 11;
    private static final int DISCORD_INVALID_SESSION = 9;

    private final DiscordManager manager;
    private final String botToken;
    private final int intents;
    private final int shardId;
    private final int shardCount;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Timer heartbeatTimer;
    private Integer lastSequence;
    private String sessionId;
    private volatile boolean closing;
    private volatile boolean heartbeatAcked = true;

    public DiscordWebSocketClient(URI serverUri, DiscordManager manager, String botToken, int intents, int shardId, int shardCount) {
        // Do not leave TCP connect timeout at the library default (0). A
        // blackholed gateway can otherwise block the retry cycle for minutes.
        super(serverUri, new Draft_6455(), Map.of(), CONNECT_TIMEOUT_MS);
        this.manager = manager;
        this.botToken = botToken;
        this.intents = intents;
        this.shardId = shardId;
        this.shardCount = shardCount;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        manager.onOpen(this);
        log.info("Discord gateway connected: {}", getURI());
    }

    @Override
    public void onMessage(String message) {
        if (!manager.isCurrentClient(this)) {
            log.debug("Ignoring Discord payload from stale gateway client: {}", getURI());
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(message);
            int op = payload.path("op").asInt(-1);

            if (payload.has("s") && !payload.get("s").isNull()) {
                lastSequence = payload.get("s").asInt();
            }

            switch (op) {
                case DISCORD_HELLO -> {
                    int heartbeatInterval = payload.path("d").path("heartbeat_interval").asInt();
                    log.info("Discord HELLO received, heartbeat interval={}ms", heartbeatInterval);
                    manager.onHello(this, heartbeatInterval);
                    if (manager.canResume()) {
                        sendResume();
                    } else {
                        sendIdentify();
                    }
                }
                case DISCORD_HEARTBEAT -> sendHeartbeat();
                case DISCORD_HEARTBEAT_ACK -> heartbeatAcked = true;
                case DISCORD_RECONNECT -> manager.onReconnectRequested(this);
                case DISCORD_INVALID_SESSION -> manager.onInvalidSession(this, payload.path("d").asBoolean(false));
                case 0 -> handleDispatch(payload);
                default -> log.debug("Discord gateway opcode {} ignored", op);
            }
        } catch (Exception e) {
            log.error("Failed to process Discord gateway payload", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("Discord gateway closed: code={}, reason={}, remote={}", code, reason, remote);

        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }

        if (closing) {
            return;
        }

        manager.onClose(this, code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        manager.onError(ex);
    }

    public void shutdown() {
        closing = true;
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        close();
    }

    public void startHeartbeat(int intervalMs) {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }

        heartbeatAcked = true;
        heartbeatTimer = new Timer(true);
        long initialDelay = java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1L, intervalMs));
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (!heartbeatAcked) {
                        log.warn("Discord heartbeat ACK missing, disconnecting");
                        handleHeartbeatTimeout();
                        return;
                    }
                    sendHeartbeat();
                } catch (Exception e) {
                    log.error("Discord heartbeat failed", e);
                }
            }
        }, initialDelay, intervalMs);
    }

    void handleHeartbeatTimeout() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        manager.onReconnectRequested(this);
        close(HEARTBEAT_TIMEOUT_CLOSE_CODE, "Heartbeat ACK missing");
    }

    public void markHeartbeatAck() {
        heartbeatAcked = true;
    }

    private void handleDispatch(JsonNode payload) {
        String eventType = payload.path("t").asText(null);
        JsonNode eventData = payload.path("d");
        Integer sequence = payload.has("s") && !payload.get("s").isNull() ? payload.get("s").asInt() : null;

        if ("READY".equals(eventType)) {
            sessionId = eventData.path("session_id").asText(null);
            String resumeGateway = eventData.path("resume_gateway_url").asText(null);
            if (resumeGateway != null && !resumeGateway.isBlank()) {
                log.info("Discord READY received: session_id={}, resume_gateway_url={}", sessionId, resumeGateway);
            } else {
                log.info("Discord READY received: session_id={}", sessionId);
            }
        } else if ("RESUMED".equals(eventType)) {
            log.info("Discord RESUMED received");
        } else if ("MESSAGE_CREATE".equals(eventType)) {
            handleMessageCreate(eventData);
        } else if ("INTERACTION_CREATE".equals(eventType)) {
            handleInteractionCreate(eventData);
        }

        manager.onDispatch(this, eventType, eventData, sequence);
    }

    private void handleMessageCreate(JsonNode eventData) {
        DiscordUser user = buildUser(eventData.path("author"), eventData.path("guild_id").asText(null), eventData.path("channel_id").asText(null));
        DiscordMessage message = new DiscordMessage(
                user.getPlatform(),
                eventData.path("id").asText(null),
                eventData.path("content").asText(""),
                eventData.path("timestamp").asText(null),
                parseMentions(eventData.path("mentions"), eventData.path("guild_id").asText(null), eventData.path("channel_id").asText(null)),
                eventData.path("guild_id").asText(null),
                eventData.path("channel_id").asText(null),
                eventData
        );

        DiscordMessageCreateEvent messageEvent = new DiscordMessageCreateEvent(user, message, message.getGuildId(), message.getChannelId());
        ThreadManager.execute(() -> EventManager.getInstance().callEvent(messageEvent));

    }

    private void handleInteractionCreate(JsonNode eventData) {
        if (eventData.path("type").asInt(-1) != 2) {
            return;
        }

        JsonNode memberNode = eventData.path("member");
        JsonNode userNode = memberNode.path("user").isMissingNode() ? eventData.path("user") : memberNode.path("user");
        String guildId = eventData.path("guild_id").asText(null);
        String channelId = eventData.path("channel_id").asText(null);
        DiscordUser user = buildUser(userNode, guildId, channelId);

        DiscordSlashCommandEvent slashCommandEvent = new DiscordSlashCommandEvent(
                user,
                eventData.path("application_id").asText(null),
                eventData.path("id").asText(null),
                eventData.path("token").asText(null),
                guildId,
                channelId,
                eventData.path("data").path("name").asText(null),
                eventData.path("data").path("options"),
                eventData.path("data").path("resolved"),
                eventData.path("timestamp").asText(null),
                eventData
        );

        ThreadManager.execute(() -> EventManager.getInstance().callEvent(slashCommandEvent));
    }

    private DiscordUser buildUser(JsonNode userNode, String guildId, String channelId) {
        boolean bot = userNode.path("bot").asBoolean(false);
        String userId = userNode.path("id").asText(null);
        String username = firstNonBlank(
                userNode.path("username").asText(null),
                userNode.path("global_name").asText(null),
                userNode.path("display_name").asText(null),
                userId
        );
        return new DiscordUser(
                guildId != null ? Platform.DISCORD_GUILD : Platform.DISCORD_DM,
                bot,
                userId,
                username,
                PlatformRole.MEMBER,
                userNode,
                guildId,
                channelId,
                userNode
        );
    }

    private List<User> parseMentions(JsonNode mentionsNode, String guildId, String channelId) {
        List<User> mentions = new ArrayList<>();
        if (!mentionsNode.isArray()) {
            return mentions;
        }
        for (JsonNode mention : mentionsNode) {
            mentions.add(buildUser(mention, guildId, channelId));
        }
        return mentions;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void sendIdentify() {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("op", DISCORD_IDENTIFY);

            ObjectNode data = payload.putObject("d");
            data.put("token", botToken);
            data.put("intents", intents);

            ArrayNode shard = data.putArray("shard");
            shard.add(shardId);
            shard.add(shardCount);

            ObjectNode properties = data.putObject("properties");
            properties.put("$os", detectOs());
            properties.put("$browser", "java-websocket");
            properties.put("$device", "AtriMeow");

            send(objectMapper.writeValueAsString(payload));
            log.info("Discord IDENTIFY sent");
        } catch (Exception e) {
            log.error("Failed to send Discord IDENTIFY", e);
        }
    }

    private void sendResume() {
        String session = manager.getSessionId();
        Integer sequence = manager.getLastSequence();
        if (session == null || sequence == null) {
            sendIdentify();
            return;
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("op", DISCORD_RESUME);

            ObjectNode data = payload.putObject("d");
            data.put("token", botToken);
            data.put("session_id", session);
            data.put("seq", sequence);

            send(objectMapper.writeValueAsString(payload));
            log.info("Discord RESUME sent: session_id={}, seq={}", session, sequence);
        } catch (Exception e) {
            log.error("Failed to send Discord RESUME", e);
        }
    }

    private void sendHeartbeat() {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("op", DISCORD_HEARTBEAT);
            if (lastSequence == null) {
                payload.putNull("d");
            } else {
                payload.put("d", lastSequence);
            }
            heartbeatAcked = false;
            send(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to send Discord heartbeat", e);
        }
    }

    private String detectOs() {
        String osName = System.getProperty("os.name", "linux").toLowerCase();
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("mac")) {
            return "macos";
        }
        return "linux";
    }
}
