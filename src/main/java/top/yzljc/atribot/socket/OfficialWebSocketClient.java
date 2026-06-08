package top.yzljc.atribot.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.debug.OfficialBotDebug;
import top.yzljc.atribot.event.Author;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.impl.*;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.service.official.OfficialTokenManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
public class OfficialWebSocketClient extends WebSocketClient {

    private final OfficialTokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Timer heartbeatTimer;
    private Integer lastSeq = null;
    private String sessionId = null;
    private volatile boolean closing;

    public OfficialWebSocketClient(URI serverUri, OfficialTokenManager tokenManager) {
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

    private void handleEvent(String eventType, JsonNode eventData) {
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
                String timestamp = eventData.get("timestamp").asText();
                String memberId = eventData.path("author").get("member_openid").asText();
                boolean isBot = eventData.path("author").get("bot").asBoolean(false);
                String id = eventData.path("author").get("id").asText();
                String username = eventData.path("author").get("username").asText();
                String unionOpenId = eventData.path("author").get("union_openid").asText();
                String groupId = eventData.path("group_id").asText();
                Object attachment = eventData.has("attachments") ? eventData.get("attachments") : null;
                Object msgRef = eventData.has("msg_elements") ? eventData.get("msg_elements") : null;

                OfficialGroupAtMessageCreateEvent event = new OfficialGroupAtMessageCreateEvent(isBot, id, msgId, groupId, groupOpenId, content, timestamp, username, memberId, unionOpenId, attachment, msgRef);
                EventManager.getInstance().callEvent(event);
            } catch (Exception e) {
                log.error("在解析官方机器人接收到的群消息事件时发生错误：", e);
            }
        }

        if ("C2C_MESSAGE_CREATE".equals(eventType)) {
            try {
                String content = eventData.get("content").asText();
                String msgId = eventData.get("id").asText();
                String timestamp = eventData.get("timestamp").asText();
                String userId = eventData.path("author").get("user_openid").asText();
                String id = eventData.path("author").get("id").asText();
                String unionOpenId = eventData.path("author").get("union_openid").asText();
                String username = eventData.path("author").get("username").asText();
                boolean isBot = eventData.path("author").get("bot").asBoolean(false);
                Object attachment = eventData.has("attachments") ? eventData.get("attachments") : null;

                OfficialC2CMessageEvent event = new OfficialC2CMessageEvent(isBot, userId, username, id, msgId, content, timestamp, unionOpenId, attachment);
                EventManager.getInstance().callEvent(event);
            } catch (Exception e) {
                log.error("在解析官方机器人接收到的群消息事件时发生错误：", e);
            }
        }

        if ("GROUP_MESSAGE_CREATE".equals(eventType)) {
            try {

                JsonNode authorNode = eventData.path("author");

                Author author = new Author(
                        authorNode.path("bot").asBoolean(false),
                        authorNode.path("id").asText(null),
                        authorNode.path("member_openid").asText(null),
                        authorNode.path("union_openid").asText(null),
                        authorNode.path("username").asText(null)
                );

                List<OfficialGroupMessageCreateEvent.Mention> mentions = new ArrayList<>();

                JsonNode mentionsNode = eventData.path("mentions");

                if (mentionsNode.isArray()) {
                    for (JsonNode mentionNode : mentionsNode) {
                        mentions.add(new OfficialGroupMessageCreateEvent.Mention(mentionNode.path("bot").asBoolean(false),
                                mentionNode.path("id").asText(null), mentionNode.path("is_you").asBoolean(false),
                                mentionNode.path("member_openid").asText(null), mentionNode.path("scope").asText(null),
                                mentionNode.path("username").asText(null))
                        );
                    }
                }

                JsonNode sceneNode = eventData.path("message_scene");
                List<String> ext = new ArrayList<>();
                JsonNode extNode = sceneNode.path("ext");
                if (extNode.isArray()) {

                    for (JsonNode node : extNode) {
                        ext.add(node.asText());
                    }
                }

                OfficialGroupMessageCreateEvent.MessageScene messageScene = new OfficialGroupMessageCreateEvent.MessageScene(ext, sceneNode.path("source").asText(null));

                OfficialGroupMessageCreateEvent event =
                        new OfficialGroupMessageCreateEvent(
                                author,
                                eventData.path("content").asText(null),
                                eventData.path("group_id").asText(null),
                                eventData.path("group_openid").asText(null),
                                eventData.path("id").asText(null),
                                mentions,
                                messageScene,
                                eventData.has("message_type") ? eventData.path("message_type").asInt() : null,
                                eventData.path("timestamp").asText(null),
                                eventData.path("attachments").isMissingNode() ? null : eventData.path("attachments"),
                                eventData.path("ark_data").isMissingNode() ? null : eventData.path("ark_data"),
                                eventData.has("msg_elements") ? eventData.get("msg_elements") : null
                        );

                EventManager.getInstance().callEvent(event);

            } catch (Exception e) {
                log.error("在解析官方机器人接收到的群消息事件时发生错误：", e);
            }
        }

        if ("GROUP_ADD_ROBOT".equals(eventType)) {
            try {
                String groupOpenId = eventData.get("group_openid").asText();
                String timestamp = eventData.get("timestamp").asText();
                String opMemberGroupId = eventData.path("op_member_openid").asText();

                OfficialGroupJoinEvent event = new OfficialGroupJoinEvent(groupOpenId, opMemberGroupId, timestamp);
                EventManager.getInstance().callEvent(event);
            } catch (Exception e) {
                log.error("在解析官方机器人接收到的加群事件时发生错误：", e);
            }
        }

        if ("GROUP_DEL_ROBOT".equals(eventType)) {
            try {
                String groupOpenId = eventData.get("group_openid").asText();
                String timestamp = eventData.get("timestamp").asText();
                String opMemberGroupId = eventData.get("op_member_openid").asText();

                OfficialGroupDelEvent event = new OfficialGroupDelEvent(groupOpenId, opMemberGroupId, timestamp);
                EventManager.getInstance().callEvent(event);
            } catch (Exception e) {
                log.error("在解析官方机器人接收到的退群事件时发生错误：", e);
            }
        }

        if ("FRIEND_ADD".equals(eventType)) {
            try {
                String userOpenId = eventData.path("author").get("union_openid").asText();
                String timestamp = eventData.get("timestamp").asText();

                OfficialFriendAddEvent event = new OfficialFriendAddEvent(userOpenId, timestamp);
                EventManager.getInstance().callEvent(event);
            } catch (Exception e) {
                log.error("在解析官方机器人接收到的好友添加事件时发生错误：", e);
            }
        }

        if ("FRIEND_DEL".equals(eventType)) {
            try {
                String userOpenId = eventData.path("author").get("union_openid").asText();
                String timestamp = eventData.get("timestamp").asText();

                OfficialFriendDelEvent event = new OfficialFriendDelEvent(userOpenId, timestamp);
                EventManager.getInstance().callEvent(event);
            } catch (Exception e) {
                log.error("在解析官方机器人接收到的好友删除事件时发生错误：", e);
            }
        }

        if ("INTERACTION_CREATE".equals(eventType)) {
            try {
                JsonNode dataNode = eventData.path("data");

                OfficialInteractionEvent event = new OfficialInteractionEvent(
                        eventData.path("chat_type").asInt(-1),
                        new OfficialInteractionEvent.Data(
                                dataNode.path("resolved"),
                                dataNode.path("type").asInt(-1)
                        ),
                        eventData.path("group_openid").asText(null),
                        eventData.path("group_member_openid").asText(
                                eventData.path("user_openid").asText(null)
                        ),
                        eventData.path("id").asText(null),
                        eventData.path("scene").asText(null),
                        eventData.path("timestamp").asText(null),
                        eventData.path("type").asInt(-1)
                );

                EventManager.getInstance().callEvent(event);

            } catch (Exception e) {
                log.error("在解析官方机器人接收到的交互事件时发生错误：", e);
            }
        }

        if (OfficialBotDebug.isOfficialDebugEnabled.get()) {
            if ("GROUP_MESSAGE_CREATE".equals(eventType) && eventData.path("author").path("union_openid").asText(null).equalsIgnoreCase("68FA9563EC62B0F43E9BE5B3023B860F")) {
                return;
            }
            log.debug(eventData.toString());
            GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(), "事件类型: " + eventType + "\n事件数据: " + eventData);
        }

        if (Config.getInstance().isDebugMode()) {
            log.debug("{}\n{}", eventType, eventData);
        }
    }
}
