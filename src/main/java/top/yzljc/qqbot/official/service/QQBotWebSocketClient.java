package top.yzljc.qqbot.official.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import top.yzljc.qqbot.event.EventManager;
import top.yzljc.qqbot.event.impl.OfficialGroupChatEvent;
import top.yzljc.qqbot.event.impl.OfficialPrivateChatEvent;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
public class QQBotWebSocketClient extends WebSocketClient {

    private final QQBotTokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Timer heartbeatTimer;
    private Integer lastSeq = null;
    private String sessionId = null;

    public QQBotWebSocketClient(URI serverUri, QQBotTokenManager tokenManager) {
        super(serverUri);
        this.tokenManager = tokenManager;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("QQ-Bot-Official WebSocket 连接已打开，正在等待 Hello 消息...");
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
                    handleEvent(eventType, payload.get("d"));
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

        new Thread(() -> {
            try {
                log.info("等待 3 秒后尝试重新连接...");
                Thread.sleep(3000);
                this.reconnectBlocking(); // 这里重连后会触发 onOpen -> 收到 Hello -> 调 sendResume/sendIdentify
            } catch (Exception e) {
                log.error("重新连接异常", e);
            }
        }).start();
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

            int intents = INTENT_GROUP_AND_C2C | INTENT_INTERACTION | INTENT_DIRECT_MESSAGE;
            d.put("intents", intents);

            d.putArray("shard").add(0).add(1);

            ObjectNode properties = d.putObject("properties");
            properties.put("$os", "linux");
            properties.put("$browser", "java-websocket");
            properties.put("$device", "spring-boot");

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
            // 【重点】每次恢复会话时，实时获取最新 Token
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

    private void handleEvent(String eventType, JsonNode eventData) {
        log.info("收到事件: {}", eventType);

        if ("READY".equals(eventType)) {
            sessionId = eventData.get("session_id").asText();
            log.info("鉴权成功！获取到 session_id: {}", sessionId);
            return;
        }

        if ("GROUP_AT_MESSAGE_CREATE".equals(eventType)) {
            try {
                String content = eventData.get("content").asText();
                String msgId = eventData.get("id").asText();
                String groupOpenId = eventData.get("group_openid").asText();
                long timestamp = eventData.get("timestamp").asLong();
                String memberId = eventData.path("author").get("member_openid").asText();
                Object attachment = eventData.has("attachments") ? eventData.get("attachments") : null;

                OfficialGroupChatEvent event = new OfficialGroupChatEvent(msgId, groupOpenId, content, timestamp, memberId, attachment);
                EventManager.getInstance().callEvent(event);
            } catch (Exception e) {
                log.error("在解析官方机器人接收到的群消息事件时发生错误：", e);
            }
        }

        if ("C2C_MESSAGE_CREATE".equals(eventType)) {
            try {
                String content = eventData.get("content").asText();
                String msgId = eventData.get("id").asText();
                long timestamp = eventData.get("timestamp").asLong();
                String memberId = eventData.path("author").get("user_openid").asText();
                Object attachment = eventData.has("attachments") ? eventData.get("attachments") : null;

                OfficialPrivateChatEvent event = new OfficialPrivateChatEvent(msgId, content, timestamp, memberId, attachment);
                EventManager.getInstance().callEvent(event);
            } catch (Exception e) {
                log.error("在解析官方机器人接收到的群消息事件时发生错误：", e);
            }
        }
    }
}