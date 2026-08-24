package top.yzljc.atribot.platform.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.CommandDefinition;
import top.yzljc.atribot.command.CommandFeature;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.command.CommandOptionDefinition;
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
    private int reconnectAttempts;

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
        reconnectPending = false;
        reconnectAttempts = 0;
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
        reconnectPending = false;
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
        reconnectAttempts = 0;
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

    synchronized void onClose(DiscordWebSocketClient source, int code, String reason, boolean remote) {
        if (stopping) {
            return;
        }

        // A previous connection can finish closing after a replacement has
        // already been installed. Its callback must not schedule a reconnect
        // that shuts down the replacement connection.
        if (source != null && webSocketClient != source) {
            return;
        }
        if (source != null) {
            webSocketClient = null;
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
        scheduleReconnect(canResume(), nextReconnectDelay());
    }

    synchronized void onClose(int code, String reason, boolean remote) {
        onClose(null, code, reason, remote);
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

        // The scheduled attempt is now running. A failure from this attempt
        // must be allowed to enqueue the next one from onClose/catch below.
        reconnectPending = false;

        if (webSocketClient != null) {
            webSocketClient.shutdown();
            webSocketClient = null;
        }

        URI uri = resolveGatewayUri(resumePreferred);
        connect(uri);
    }

    private synchronized void scheduleReconnect(boolean resumePreferred, long delayMs) {
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
                synchronized (DiscordManager.this) {
                    reconnectPending = false;
                }
                scheduleReconnect(resumePreferred, nextReconnectDelay());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private synchronized long nextReconnectDelay() {
        int exponent = Math.min(reconnectAttempts++, 5);
        return Math.min(30_000L, 1_000L << exponent);
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
            payload.add(buildCommandNode(definition));
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

    private ObjectNode buildCommandNode(CommandDefinition definition) {
        ObjectNode commandNode = objectMapper.createObjectNode();
        commandNode.put("name", definition.name());
        commandNode.put("type", 1);
        String description = definition.description();
        commandNode.put("description", description == null || description.isBlank() ? definition.name() : description);

        if (definition.options() != null && !definition.options().isEmpty()) {
            ArrayNode optionsArray = objectMapper.createArrayNode();
            for (CommandOptionDefinition option : definition.options()) {
                if (option == null) {
                    continue;
                }
                ObjectNode optionNode = buildOptionNode(option);
                if (optionNode != null) {
                    optionsArray.add(optionNode);
                }
            }
            if (!optionsArray.isEmpty()) {
                commandNode.set("options", optionsArray);
            }
        }
        return commandNode;
    }

    /**
     * 把 {@link CommandOptionDefinition} 转译为 Discord ApplicationCommandOption JSON。
     * type 字段为 Discord 官方编号：1=SUB_COMMAND、2=SUB_COMMAND_GROUP、3=STRING、
     * 4=INTEGER、5=BOOLEAN、6=USER、7=CHANNEL、8=ROLE、9=MENTIONABLE、10=NUMBER、11=ATTACHMENT。
     * 校验失败（如 required 写在 sub_command 上）仅 log warn 并丢弃该 option，避免阻断 bot 启动。
     */
    private ObjectNode buildOptionNode(CommandOptionDefinition option) {
        String name = option.name();
        if (name == null || name.isBlank()) {
            log.warn("Discord option skipped: missing name");
            return null;
        }
        int type = option.type();
        if (type < 1 || type > 11) {
            log.warn("Discord option '{}' skipped: invalid type {}", name, type);
            return null;
        }
        if (name.length() > 32) {
            log.warn("Discord option '{}' skipped: name exceeds 32 chars", name);
            return null;
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("type", type);
        String description = option.description();
        node.put("description", description == null || description.isBlank() ? name : description);

        boolean isLeaf = type >= 3 && type <= 11;
        boolean isGroup = type == 1 || type == 2;

        if (isLeaf && option.required()) {
            node.put("required", true);
        } else if (isGroup && option.required()) {
            log.warn("Discord option '{}' (sub_command/group) cannot be required; ignored", name);
        }

        if (type == 3 || type == 4 || type == 10) {
            // STRING / INTEGER / NUMBER 支持 choices
            if (!option.choices().isEmpty()) {
                if (option.choices().size() > 25) {
                    log.warn("Discord option '{}' choices exceed 25, truncated", name);
                }
                ArrayNode choicesArray = objectMapper.createArrayNode();
                int count = 0;
                for (CommandOptionDefinition.OptionChoice choice : option.choices()) {
                    if (count++ >= 25) {
                        break;
                    }
                    if (choice.name() == null || choice.name().isBlank() || choice.value() == null) {
                        continue;
                    }
                    ObjectNode choiceNode = objectMapper.createObjectNode();
                    choiceNode.put("name", choice.name());
                    choiceNode.set("value", choice.value());
                    choicesArray.add(choiceNode);
                }
                node.set("choices", choicesArray);
            }
        }

        if (type == 4 || type == 10) {
            // INTEGER / NUMBER 支持 min_value / max_value
            if (option.minValue() != null) {
                node.put("min_value", option.minValue());
            }
            if (option.maxValue() != null) {
                node.put("max_value", option.maxValue());
            }
        }

        if (type == 7) {
            // CHANNEL 支持 channel_types
            if (!option.channelTypes().isEmpty()) {
                ArrayNode channelTypesArray = objectMapper.createArrayNode();
                for (Integer channelType : option.channelTypes()) {
                    if (channelType != null) {
                        channelTypesArray.add(channelType);
                    }
                }
                node.set("channel_types", channelTypesArray);
            }
        }

        if (isGroup && !option.options().isEmpty()) {
            ArrayNode nested = objectMapper.createArrayNode();
            for (CommandOptionDefinition child : option.options()) {
                if (child == null) {
                    continue;
                }
                ObjectNode childNode = buildOptionNode(child);
                if (childNode != null) {
                    nested.add(childNode);
                }
            }
            if (!nested.isEmpty()) {
                node.set("options", nested);
            }
        }

        return node;
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
