package top.yzljc.atribot.platform.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.event.impl.InteractionType;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName BotEvents
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.official
 */
@Slf4j
public class BotEvents {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void handleGroupChatEvent(JsonNode eventData) {
        try {
            boolean isBot = eventData.path("author").get("bot").asBoolean(false);
            String content = eventData.get("content").asText();
            String groupOpenId = eventData.get("group_openid").asText(null);
            String username = eventData.path("author").get("username").asText();
            String unionOpenId = eventData.path("author").get("member_openid").asText(null);
            String messageId = eventData.path("id").asText(null);
            String timestamp = eventData.get("timestamp").asText();
            int messageType = eventData.path("message_type").asInt(-1);
            JsonNode attachment = eventData.has("attachments") ? eventData.get("attachments") : null;
            JsonNode msgRef = eventData.has("msg_elements") ? eventData.get("msg_elements") : null;
            JsonNode ark = eventData.path("ark_data").isMissingNode() ? null : eventData.path("ark_data");
            String extValue = eventData.path("message_scene").path("ext").get(0).asText();
            String refIdx = extValue.substring(8);

            PlatformRole role = PlatformRole.getPlatformRole(eventData.path("author").get("member_role").asText());

            List<User> mentions = new ArrayList<>();
            var mentionsNode = eventData.path("mentions");
            boolean isAtBot = false;

            if (mentionsNode.isArray()) {
                for (JsonNode mentionNode : mentionsNode) {
                    var scope = mentionNode.path("scope").asText("single");
                    if (scope.equals("all")) continue;
                    var user_isBot = mentionNode.path("bot").asBoolean(false);
                    var user_id = mentionNode.path("member_openid").asText(null);
                    var user_username = mentionNode.path("username").asText(null);
                    if (mentionNode.path("is_you").asBoolean()) {
                        isAtBot = true;
                    }
                    PlatformRole roleMentioned = PlatformRole.getPlatformRole(eventData.path("author").get("member_role").asText());
                    mentions.add(new User(Platform.OFFICIAL_GROUP, user_isBot, user_id, user_username, roleMentioned, mapper.createObjectNode()));
                }
            }

            User sender = new User(Platform.OFFICIAL_GROUP, isBot, unionOpenId, username, role, mapper.createObjectNode());
            OfficialMessage msg = new OfficialMessage(Platform.OFFICIAL_GROUP, messageId, content, timestamp, mentions, messageType, refIdx, attachment, ark, msgRef);
            OfficialGroupMessageCreateEvent event = new OfficialGroupMessageCreateEvent(sender, groupOpenId, msg, timestamp, isAtBot);
            EventManager.getInstance().callEvent(event);

        } catch (Exception e) {
            log.error("在解析官方机器人接收到的群消息事件时发生错误：", e);
        }
    }

    public static void handleC2CChatEvent(JsonNode eventData) {
        try {
            boolean isBot = eventData.path("author").get("bot").asBoolean(false);
            String content = eventData.get("content").asText();
            String username = eventData.path("author").get("username").asText();
            String unionOpenId = eventData.path("author").get("user_openid").asText(null);
            String messageId = eventData.path("id").asText(null);
            int messageType = eventData.path("message_type").asInt(-1);
            String timestamp = eventData.get("timestamp").asText();
            JsonNode attachment = eventData.has("attachments") ? eventData.get("attachments") : null;
            JsonNode msgRef = eventData.has("msg_elements") ? eventData.get("msg_elements") : null;
            JsonNode ark = eventData.path("ark_data").isMissingNode() ? null : eventData.path("ark_data");
            String extValue = eventData.path("message_scene").path("ext").get(0).asText();
            String refIdx = extValue.substring(8);

            User sender = new User(Platform.OFFICIAL_C2C, isBot, unionOpenId, username, PlatformRole.MEMBER, mapper.createObjectNode());
            OfficialMessage msg = new OfficialMessage(Platform.OFFICIAL_C2C, messageId, content, timestamp, List.of(), messageType, refIdx, attachment, ark, msgRef);
            OfficialC2CMessageCreateEvent event = new OfficialC2CMessageCreateEvent(sender, msg, timestamp);
            EventManager.getInstance().callEvent(event);
        } catch (Exception e) {
            log.error("在解析官方机器人接收到的C2C消息事件时发生错误：", e);
        }
    }

    public static void handleGroupAtChatEvent(JsonNode eventData) {
        try {
            boolean isBot = eventData.path("author").get("bot").asBoolean(false);
            String content = eventData.get("content").asText();
            String groupOpenId = eventData.get("group_openid").asText(null);
            String username = eventData.path("author").get("username").asText();
            String unionOpenId = eventData.path("author").get("member_openid").asText(null);
            String messageId = eventData.path("id").asText(null);
            int messageType = eventData.path("message_type").asInt(-1);
            String timestamp = eventData.get("timestamp").asText();
            JsonNode attachment = eventData.has("attachments") ? eventData.get("attachments") : null;
            JsonNode msgRef = eventData.has("msg_elements") ? eventData.get("msg_elements") : null;
            JsonNode ark = eventData.path("ark_data").isMissingNode() ? null : eventData.path("ark_data");
            String extValue = eventData.path("message_scene").path("ext").get(0).asText();
            String refIdx = extValue.substring(8);

            PlatformRole role = PlatformRole.getPlatformRole(eventData.path("author").get("member_role").asText());

            List<User> mentions = new ArrayList<>();
            var mentionsNode = eventData.path("mentions");

            if (mentionsNode.isArray()) {
                for (JsonNode mentionNode : mentionsNode) {
                    var user_isBot = mentionNode.path("bot").asBoolean(false);
                    var user_id = mentionNode.path("member_openid").asText(null);
                    var user_username = mentionNode.path("username").asText(null);
                    PlatformRole roleMentioned = PlatformRole.getPlatformRole(eventData.path("author").get("member_role").asText());
                    mentions.add(new User(Platform.OFFICIAL_GROUP, user_isBot, user_id, user_username, roleMentioned, mapper.createObjectNode()));
                }
            }

            User sender = new User(Platform.OFFICIAL_GROUP, isBot, unionOpenId, username, role, mapper.createObjectNode());
            OfficialMessage msg = new OfficialMessage(Platform.OFFICIAL_GROUP, messageId, content, timestamp, mentions, messageType, refIdx, attachment, ark, msgRef);
            OfficialGroupAtMessageCreateEvent event = new OfficialGroupAtMessageCreateEvent(sender, msg, groupOpenId, timestamp);
            EventManager.getInstance().callEvent(event);
        } catch (Exception e) {
            log.error("在解析官方机器人接收到的群At消息事件时发生错误：", e);
        }
    }

    public static void handleGroupJoinEvent(JsonNode eventData) {
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

    public static void handleGroupDelEvent(JsonNode eventData) {
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

    public static void handleFriendAddEvent(JsonNode eventData) {
        try {
            String userOpenId = eventData.path("author").get("union_openid").asText();
            if (userOpenId.isEmpty()) {
                userOpenId = eventData.path("openid").asText(null);
            }
            String timestamp = eventData.get("timestamp").asText();

            OfficialFriendAddEvent event = new OfficialFriendAddEvent(userOpenId, timestamp);
            EventManager.getInstance().callEvent(event);
        } catch (Exception e) {
            log.error("在解析官方机器人接收到的好友添加事件时发生错误：", e);
        }
    }

    public static void handleFriendRemoveEvent(JsonNode eventData) {
        try {
            String userOpenId = eventData.path("author").get("union_openid").asText();
            if (userOpenId.isEmpty()) {
                userOpenId = eventData.path("openid").asText(null);
            }
            String timestamp = eventData.get("timestamp").asText();

            OfficialFriendDelEvent event = new OfficialFriendDelEvent(userOpenId, timestamp);
            EventManager.getInstance().callEvent(event);
        } catch (Exception e) {
            log.error("在解析官方机器人接收到的好友删除事件时发生错误：", e);
        }
    }

    public static void handleInteractionEvent(String eventId, JsonNode eventData) {
        try {
            JsonNode dataNode = eventData.path("data");
            String application_id = eventData.path("application_id").asText(null);
            String id = eventData.path("id").asText(null);
            String scene = eventData.path("scene").asText(null);
            String timestamp = eventData.path("timestamp").asText(null);
            int type = eventData.path("type").asInt(-1);
            int version = eventData.path("version").asInt(-1);
            int chatType = eventData.path("chat_type").asInt(-1);
            String groupId = eventData.path("group_openid").asText(null);
            String userId = eventData.path("group_member_openid").asText(eventData.path("user_openid").asText(null));

            var interactionType = InteractionType.from(type);

            switch (interactionType) {
                case BUTTON_CLICK -> {
                    OfficialButtonInteractionEvent event = new OfficialButtonInteractionEvent(
                            application_id,
                            eventId,
                            chatType,
                            new OfficialButtonInteractionEvent.Data(
                                    dataNode.path("resolved"),
                                    dataNode.path("type").asInt(-1)
                            ),
                            groupId,
                            userId,
                            id,
                            scene,
                            timestamp,
                            type,
                            version
                    );
                    EventManager.getInstance().callEvent(event);
                }
                case USER_AUTHORIZE -> {
                    OfficialC2CAuthorizeModifyEvent event = new OfficialC2CAuthorizeModifyEvent(
                            application_id,
                            eventId,
                            userId,
                            id,
                            scene,
                            timestamp,
                            type,
                            version,
                            dataNode.path("resolved").path("authorize_data")
                    );
                    EventManager.getInstance().callEvent(event);
                }
                case GROUP_AUTHORIZE_STATUS -> {
                    log.info("收到群开发者设置事件，内容: {}", eventData.toString());
                }
                default -> {
                    log.warn("收到非常牛逼的新的交互事件类型: {}, 内容: {}", type, eventData.toString());
                }
            }
        } catch (Exception e) {
            log.error("在解析官方机器人接收到的交互事件时发生错误：", e);
        }
    }

    public static void handleGroupMemberAddEvent(String eventId, JsonNode eventData) {
        try {
            OfficialGroupMemberAddEvent event = new OfficialGroupMemberAddEvent(
                    eventId,
                    eventData.path("group_openid").asText(null),
                    eventData.path("member_openid").asText(null),
                    eventData.path("timestamp").asText(null)
            );

            EventManager.getInstance().callEvent(event);
        } catch (Exception e) {
            log.error("在解析官方机器人接收到的群成员添加事件时发生错误：", e);
        }
    }

    public static void handleGroupMemberRemoveEvent(String eventId, JsonNode eventData) {
        try {
            OfficialGroupMemberRemoveEvent event = new OfficialGroupMemberRemoveEvent(
                    eventId,
                    eventData.path("group_openid").asText(null),
                    eventData.path("member_openid").asText(null),
                    eventData.path("timestamp").asText(null)
            );

            EventManager.getInstance().callEvent(event);
        } catch (Exception e) {
            log.error("在解析官方机器人接收到的群成员删除事件时发生错误：", e);
        }
    }

    public static void handleMessageAuditRejectEvent(JsonNode evetData) {
        Alert.notify("消息审核失败: " + evetData.toString());
        log.error("消息审核失败: {}", evetData);
    }
}