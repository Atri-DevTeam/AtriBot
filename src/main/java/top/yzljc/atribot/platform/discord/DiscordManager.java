package top.yzljc.atribot.platform.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.CommandDefinition;
import top.yzljc.atribot.command.CommandFeature;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.command.SlashCommandExecutor;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DiscordManager {

    private static final String GATEWAY_QUERY = "v=10&encoding=json";
    private static final Set<Integer> FATAL_CLOSE_CODES = Set.of(4004, 4010, 4011, 4012, 4013, 4014);

    private final String apiBaseUrl;
    private final String botToken;
    private final int intents;
    private final int shardId;
    private final int shardCount;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String applicationId;
    private volatile URI gatewayUrl;
    private volatile URI resumeGatewayUrl;
    private volatile Integer lastSequence;
    private volatile String sessionId;
    private volatile DiscordWebSocketClient webSocketClient;
    private volatile boolean stopping;
    private volatile boolean reconnectPending;

    public DiscordManager(String apiBaseUrl, String botToken, int intents) {
        this(apiBaseUrl, botToken, intents, 0, 1);
    }

    public DiscordManager(String apiBaseUrl, String botToken, int intents, int shardId, int shardCount) {
        this.apiBaseUrl = apiBaseUrl;
        this.botToken = normalizeBotToken(botToken);
        this.intents = intents;
        this.shardId = shardId;
        this.shardCount = shardCount;
    }

    public synchronized void start() throws Exception {
        stopping = false;
        if (applicationId == null) {
            applicationId = fetchApplicationId();
        }
        registerSlashCommands();
        if (gatewayUrl == null) {
            gatewayUrl = fetchGatewayUrl();
        }
        connect(resolveGatewayUri(false));
    }

    public synchronized void stop() {
        stopping = true;
        if (webSocketClient != null) {
            webSocketClient.shutdown();
            webSocketClient = null;
        }
    }

    synchronized boolean canResume() {
        return sessionId != null && lastSequence != null && resumeGatewayUrl != null;
    }

    synchronized String getSessionId() {
        return sessionId;
    }

    synchronized Integer getLastSequence() {
        return lastSequence;
    }

    synchronized void onOpen() {
        reconnectPending = false;
    }

    synchronized void onHello(int heartbeatInterval) {
        DiscordWebSocketClient client = webSocketClient;
        if (client != null) {
            client.startHeartbeat(heartbeatInterval);
        }
    }

    synchronized void onDispatch(String eventType, JsonNode eventData, Integer sequence) {
        if (sequence != null) {
            lastSequence = sequence;
        }

        if ("READY".equals(eventType)) {
            sessionId = eventData.path("session_id").asText(null);
            String resumeUrl = eventData.path("resume_gateway_url").asText(null);
            if (resumeUrl != null && !resumeUrl.isBlank()) {
                resumeGatewayUrl = normalizeGatewayUri(URI.create(resumeUrl));
            }
            log.info("Discord READY: session_id={}, resume_gateway_url={}", sessionId, resumeGatewayUrl);
            return;
        }

        if ("RESUMED".equals(eventType)) {
            log.info("Discord session resumed successfully");
            return;
        }
    }

    synchronized void onHeartbeatAck() {
        DiscordWebSocketClient client = webSocketClient;
        if (client != null) {
            client.markHeartbeatAck();
        }
    }

    synchronized void onReconnectRequested() {
        scheduleReconnect(true, 1000L);
    }

    synchronized void onInvalidSession(boolean resumable) {
        if (!resumable) {
            clearSessionState();
        }
        long delay = resumable ? randomDelay(1000L, 5000L) : 0L;
        scheduleReconnect(resumable, delay);
    }

    synchronized void onClose(int code, String reason, boolean remote) {
        if (stopping) {
            return;
        }

        if (FATAL_CLOSE_CODES.contains(code)) {
            log.error("Discord gateway closed with fatal code={}, reason={}, remote={}", code, reason, remote);
            stopping = true;
            return;
        }

        if (reconnectPending) {
            return;
        }

        log.info("Discord gateway closed: code={}, reason={}, remote={}", code, reason, remote);
        scheduleReconnect(canResume(), 1000L);
    }

    synchronized void onError(Exception ex) {
        log.error("Discord WebSocket error", ex);
    }

    String getApplicationId() {
        return applicationId;
    }

    private void connect(URI uri) {
        if (uri == null) {
            throw new IllegalStateException("Discord gateway URI is null");
        }

        DiscordWebSocketClient client = new DiscordWebSocketClient(
                uri,
                this,
                botToken,
                intents,
                shardId,
                shardCount
        );
        webSocketClient = client;
        client.connect();
    }

    private synchronized void reconnect(boolean resumePreferred) throws Exception {
        if (stopping) {
            return;
        }

        if (webSocketClient != null) {
            webSocketClient.shutdown();
            webSocketClient = null;
        }

        URI uri = resolveGatewayUri(resumePreferred);
        connect(uri);
    }

    private void scheduleReconnect(boolean resumePreferred, long delayMs) {
        if (stopping) {
            return;
        }
        if (reconnectPending) {
            return;
        }
        reconnectPending = true;

        ThreadManager.schedule(() -> {
            try {
                reconnect(resumePreferred);
            } catch (Exception e) {
                log.error("Discord reconnect failed", e);
                reconnectPending = false;
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void clearSessionState() {
        sessionId = null;
        lastSequence = null;
        resumeGatewayUrl = null;
    }

    private URI fetchGatewayUrl() throws Exception {
        String gatewayApi = apiBaseUrl + "/gateway/bot";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayApi))
                .header("Authorization", "Bot " + botToken)
                .GET()
                .build();

        HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response.statusCode() == 401) {
                throw new IllegalStateException(
                        "Discord gateway request failed: HTTP 401. 请填写 Discord Developer Portal 里的 Bot Token，" +
                                "不是 Client Secret / App Secret；配置里也不要再手动带 \"Bot \" 前缀。body=" + response.body()
                );
            }
            throw new IllegalStateException("Discord gateway request failed: HTTP " + response.statusCode() + ", body=" + response.body());
        }

        JsonNode responseNode = objectMapper.readTree(response.body());
        String url = responseNode.path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Discord gateway response missing url: " + responseNode);
        }

        int shards = responseNode.path("shards").asInt(1);
        JsonNode sessionStartLimit = responseNode.path("session_start_limit");
        log.info("Discord gateway ready: url={}, shards={}, remaining={}", url, shards, sessionStartLimit.path("remaining").asInt(-1));
        return normalizeGatewayUri(URI.create(url));
    }

    private String fetchApplicationId() throws Exception {
        String api = apiBaseUrl + "/oauth2/applications/@me";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(api))
                .header("Authorization", "Bot " + botToken)
                .GET()
                .build();

        HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Discord application request failed: HTTP " + response.statusCode() + ", body=" + response.body());
        }

        JsonNode responseNode = objectMapper.readTree(response.body());
        String id = responseNode.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Discord application response missing id: " + responseNode);
        }
        log.info("Discord application id resolved: {}", id);
        return id;
    }

    private void registerSlashCommands() throws Exception {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalStateException("Discord application id is null");
        }

        ArrayNode payload = objectMapper.createArrayNode();
        for (CommandDefinition definition : CommandManager.getDefinitions()) {
            CommandFeature command = CommandManager.getCommand(definition.name());
            if (command == null || !(command.getExecutor() instanceof SlashCommandExecutor)) {
                continue;
            }

            ObjectNode commandNode = objectMapper.createObjectNode();
            commandNode.put("name", definition.name());
            commandNode.put("type", 1);
            String description = definition.description();
            commandNode.put("description", description == null || description.isBlank() ? definition.name() : description);
            payload.add(commandNode);
        }

        if (payload.isEmpty()) {
            log.info("Discord slash command registration skipped: no SlashCommandExecutor found");
            return;
        }

        String commandUrl = apiBaseUrl + "/applications/" + applicationId + "/commands";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(commandUrl))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Discord command registration failed: HTTP " + response.statusCode() + ", body=" + response.body());
        }
        log.info("Discord slash commands registered: {}", payload.size());
    }

    private URI resolveGatewayUri(boolean resumePreferred) {
        URI base = resumePreferred && resumeGatewayUrl != null ? resumeGatewayUrl : gatewayUrl;
        if (base == null) {
            return null;
        }
        return normalizeGatewayUri(base);
    }

    private URI normalizeGatewayUri(URI uri) {
        String raw = uri.toString();
        if (raw.contains("?")) {
            return uri;
        }
        return URI.create(raw + "?" + GATEWAY_QUERY);
    }

    private long randomDelay(long minInclusive, long maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }
        return minInclusive + ThreadLocalRandom.current().nextLong(maxInclusive - minInclusive + 1);
    }

    private String normalizeBotToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, "Bot ", 0, 4)) {
            normalized = normalized.substring(4).trim();
        } else if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized;
    }
}
