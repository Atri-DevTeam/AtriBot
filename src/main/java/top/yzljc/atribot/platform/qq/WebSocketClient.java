package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.handshake.ServerHandshake;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.EventLogRepository;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.function.general.DebugCommand;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
public class WebSocketClient extends org.java_websocket.client.WebSocketClient {

    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Timer heartbeatTimer;
    private Integer lastSeq = null;
    private String sessionId = null;
    private volatile boolean closing;

    public WebSocketClient(URI serverUri, TokenManager tokenManager) {
        super(serverUri);
        this.tokenManager = tokenManager;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("Connecting to official Tencent QQ Server...");
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message);
            int op = payload.get("op").asInt();

            if (payload.has("s") && !payload.get("s").isNull()) {
                lastSeq = payload.get("s").asInt();
            }

            switch (op) {
                case 10:
                    int heartbeatInterval = payload.get("d").get("heartbeat_interval").asInt();
                    log.info("收到 Hello，心跳周期为: {}ms", heartbeatInterval);
                    startHeartbeat(heartbeatInterval);

                    if (sessionId != null && lastSeq != null) {
                        sendResume();
                    } else {
                        sendIdentify();
                    }
                    break;
                case 0:
                    String eventType = payload.get("t").asText();
                    String eventId = payload.path("id").asText(null);
                    JsonNode eventData = payload.get("d");
                    if ("READY".equals(eventType)) {
                        handleEvent(eventType, eventId, eventData, payload);
                    } else {
                        ThreadManager.execute(() -> handleEvent(eventType, eventId, eventData, payload));
                    }
                    break;
                case 11:
                    break;
                case 7:
                    log.warn("收到 Opcode 7 (Reconnect)，服务器要求重连");
                    this.close();
                    break;
                case 9:
                    log.warn("收到 Opcode 9 (Invalid Session)，会话已失效，准备重新鉴权");
                    sessionId = null;
                    sendIdentify();
                    break;
                default:
                    log.info("收到其他 Opcode 消息: {}", message);
            }
        } catch (Exception e) {
            log.error("处理消息异常", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("WebSocket 连接关闭: code={}, reason={}, remote={}", code, reason, remote);

        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }

        if (closing) {
            return;
        }

        ThreadManager.execute(() -> {
            log.info("尝试重新连接...");
            try {
                this.reconnectBlocking();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket 发生错误", ex);
    }

    private void sendIdentify() {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("op", 2);

            ObjectNode d = payload.putObject("d");
            d.put("token", "QQBot " + tokenManager.getAccessToken());

            int INTENT_GROUP_AND_C2C = 1 << 25;
            int INTENT_INTERACTION = 1 << 26;
            int INTENT_DIRECT_MESSAGE = 1 << 12;
            int MESSAGE_AUDIT = 1 << 27;
            int INTENT_GROUP_MEMBER = 1 << 24;
            int PUBLIC_GUILD_MESSAGES = 1 << 30;
            int GUILDS = 1 << 0;
            int GUILD_MEMBERS = 1 << 1;
            int GUILD_MESSAGE_REACTIONS = 1 << 10;
            int AUDIO_ACTION = 1 << 29;
            int UNKNOWN_BUT_I_CAN_USE = 1 << 18 | 1 << 19 | 1 << 21 | 1 << 22;

            int intents = INTENT_GROUP_AND_C2C | INTENT_INTERACTION | INTENT_DIRECT_MESSAGE | MESSAGE_AUDIT | INTENT_GROUP_MEMBER | PUBLIC_GUILD_MESSAGES
                    | GUILDS | GUILD_MEMBERS | GUILD_MESSAGE_REACTIONS | AUDIO_ACTION | UNKNOWN_BUT_I_CAN_USE;
            d.put("intents", intents);

            d.putArray("shard").add(0).add(1);

            ObjectNode properties = d.putObject("properties");
            properties.put("$os", "linux");
            properties.put("$browser", "java-websocket");
            properties.put("$device", "AtriMeow");

            this.send(objectMapper.writeValueAsString(payload));
            log.info("鉴权消息 (Identify) 已发送");
        } catch (Exception e) {
            log.error("发送鉴权消息失败", e);
        }
    }

    private void sendResume() {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("op", 6);

            ObjectNode d = payload.putObject("d");
            d.put("token", "QQBot " + tokenManager.getAccessToken());
            d.put("session_id", sessionId);
            d.put("seq", lastSeq);

            this.send(objectMapper.writeValueAsString(payload));
            log.info("已发送会话恢复请求 (Resume), session_id={}, seq={}", sessionId, lastSeq);
        } catch (Exception e) {
            log.error("发送恢复请求失败", e);
        }
    }

    private void startHeartbeat(int interval) {
        heartbeatTimer = new Timer(true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.put("op", 1);
                    payload.put("d", lastSeq);
                    send(objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.error("发送心跳异常", e);
                }
            }
        }, interval, interval);
    }

    public void shutdown() {
        closing = true;
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        close();
    }

    private void recordRawEvent(String eventType, String eventId, JsonNode rawPayload) {
        try {
            // 直接存 t/d 层最原始的整包 JSON，不做任何解析
            EventLogRepository.record(eventType, eventId, lastSeq, rawPayload.toString());
        } catch (Exception e) {
            log.error("记录原始事件失败: eventType={}", eventType, e);
        }
    }

    private void handleEvent(String eventType, String eventId, JsonNode eventData, JsonNode rawPayload) {
        recordRawEvent(eventType, eventId, rawPayload);
        if ("READY".equals(eventType)) {
            sessionId = eventData.get("session_id").asText();
            log.info("鉴权成功！获取到 session_id: {}", sessionId);
            return;
        }
        if ("RESUMED".equals(eventType)) {
            log.info("会话恢复成功！");
        }
        dispatchEvent(eventType, eventId, eventData);
    }

    /**
     * Dispatches an event payload shared by WebSocket and Webhook transports.
     */
    public static void dispatchEvent(String eventType, String eventId, JsonNode eventData) {
        switch (eventType) {
            case "READY":
                return;
            case "RESUMED":
                break;
            case "GROUP_AT_MESSAGE_CREATE":
                BotEvents.handleGroupAtChatEvent(eventData);
                break;
            case "C2C_MESSAGE_CREATE":
                BotEvents.handleC2CChatEvent(eventData);
                break;
            case "GROUP_MESSAGE_CREATE":
                BotEvents.handleGroupChatEvent(eventData);
                break;
            case "GROUP_ADD_ROBOT":
                BotEvents.handleGroupJoinEvent(eventId, eventData);
                break;
            case "GROUP_DEL_ROBOT":
                BotEvents.handleGroupDelEvent(eventData);
                break;
            case "FRIEND_ADD":
                BotEvents.handleFriendAddEvent(eventData);
                break;
            case "FRIEND_DEL":
                BotEvents.handleFriendRemoveEvent(eventData);
                break;
            case "INTERACTION_CREATE":
                BotEvents.handleInteractionEvent(eventId, eventData);
                break;
            case "GROUP_MEMBER_ADD":
                BotEvents.handleGroupMemberAddEvent(eventId, eventData);
                break;
            case "GROUP_MEMBER_REMOVE":
                BotEvents.handleGroupMemberRemoveEvent(eventId, eventData);
                break;
            case "MESSAGE_AUDIT_REJECT":
                BotEvents.handleMessageAuditRejectEvent(eventData);
                break;
            case "AT_MESSAGE_CREATE":
                BotEvents.handleGuildChannelAtMessageCreateEvent(eventData);
                break;
            case "GROUP_JOIN_REQUEST":
                BotEvents.handleGroupJoinRequestEvent(eventId, eventData);
                break;
            case "DIRECT_MESSAGE_CREATE":
                BotEvents.handleGuildDirectMessageCreateEvent(eventData);
                break;
            default:
                log.info("[!] 收到新的事件类型: {}\n {}", eventType, eventData);
                break;
        }

        if (DebugCommand.isQQDebugEnabled.get()) {
            if ("GROUP_MESSAGE_CREATE".equals(eventType)
                    && eventData.path("author").path("union_openid").asText("")
                    .equalsIgnoreCase("68FA9563EC62B0F43E9BE5B3023B860F")) {
                return;
            }
            log.debug(eventData.toString());
            if (DebugCommand.type == DebugCommand.DebugDisplayType.DEBUG_GROUP) {
                GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), "事件类型: " + eventType + "\n事件数据: " + eventData);
            }
        }

        if (Config.getInstance().getEnv().equals("dev")) {
            log.debug("{}\n{}", eventType, eventData);
        }
    }
}
