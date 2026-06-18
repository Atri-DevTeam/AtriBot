package top.yzljc.atribot.platform.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.event.impl.*;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.utils.debug.NapcatPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class RequestReceiver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String handle(JsonNode root) {
        if (root != null) {
            NapcatPacket.process(root);
            try {
                String postType = root.path("post_type").asText("");

                if ("message".equals(postType)) {
                    String messageId = root.path("message_id").asText();
                    String time = root.path("time").asText();
                    String selfId = root.path("self_id").asText();
                    String userId = root.path("user_id").asText();
                    String rawMessage = root.path("raw_message").asText();
                    String messageType = root.path("message_type").asText();

                    JsonNode senderNode = root.path("sender");
                    User senderObj = null;
                    if (!senderNode.isMissingNode() && !senderNode.isNull()) {
                        senderObj = new User(
                                Platform.NAPCAT_GROUP,
                                selfId.equals(userId),
                                senderNode.path("user_id").asText(),
                                senderNode.path("nickname").asText(""),
                                PlatformRole.getPlatformRole(senderNode.path("role").asText()),
                                MAPPER.createObjectNode()
                        );
                    }

                    JsonNode attachments = root.path("message");

                    List<User> mentionList = new ArrayList<>();
                    if (rawMessage.contains("[CQ:at")) {
                        JsonNode rawNode = root.path("raw");
                        if (!rawNode.isMissingNode()) {
                            JsonNode elements = rawNode.path("elements");
                            for (JsonNode element : elements) {
                                JsonNode textElement = element.path("textElement");
                                String atNtUid = textElement.path("atNtUid").asText("");
                                if (!atNtUid.isEmpty()) {
                                    String uin = textElement.path("atUid").asText(null);
                                    JsonNode uid = MAPPER.createObjectNode().put("ntUid", atNtUid);
//                                    String content = textElement.path("content").asText("");
                                    User mentioned = new User(Platform.NAPCAT_GROUP, selfId.equals(uin), uin, "", PlatformRole.MEMBER, uid);
                                    mentionList.add(mentioned);
                                }
                            }
                        }
                    }

                    NapcatMessage message = new NapcatMessage(Platform.NAPCAT_GROUP, messageId, rawMessage, time, mentionList, attachments);

                    // 触发对应的事件
                    if ("group".equals(messageType)) {
                        String groupId = root.path("group_id").asText();
                        NapcatGroupMessageEvent event = new NapcatGroupMessageEvent(senderObj, message, groupId, time);
                        EventManager.getInstance().callEvent(event);
                    } else if ("private".equals(messageType)) {
                        NapcatPrivateMessageEvent event = new NapcatPrivateMessageEvent(senderObj, message, time);
                        EventManager.getInstance().callEvent(event);
                    }
                }
                // 群成员变动事件
                if ("notice".equals(postType) && root.has("group_id") && !root.path("notice_type").asText().equals("group_recall")) {
                    String time = root.path("time").asText();
                    String groupId = root.path("group_id").asText();
                    String userId = root.path("user_id").asText();
                    String selfId = root.path("self_id").asText();
                    String operatorId = root.path("operator_id").asText();
                    String subType = root.path("sub_type").asText();
                    NapcatGroupMemberChangeEvent event = new NapcatGroupMemberChangeEvent(time, selfId, groupId, userId, operatorId, subType);
                    EventManager.getInstance().callEvent(event);
                }

                // 群管理事件监听
                if ("request".equals(postType) && "group".equals(root.path("request_type").asText())) {
                    String time = root.path("time").asText();
                    String groupId = root.path("group_id").asText();
                    String userId = root.path("user_id").asText();
                    String selfId = root.path("self_id").asText();
                    String subType = root.path("sub_type").asText();
                    String flag = root.path("flag").asText();
                    String comment = root.path("comment").asText();
                    NapcatGroupRequestEvent event = new NapcatGroupRequestEvent(time, selfId, groupId, userId, flag, subType, comment);
                    EventManager.getInstance().callEvent(event);
                }
                // 加好友事件监听器
                if ("request".equals(postType) && "friend".equals(root.path("request_type").asText())) {
                    String time = root.path("time").asText();
                    String userId = root.path("user_id").asText();
                    String selfId = root.path("self_id").asText();
                    String flag = root.path("flag").asText();

                    NapcatFriendRequestEvent event = new NapcatFriendRequestEvent(time, selfId, userId, flag);
                    EventManager.getInstance().callEvent(event);
                }
                // 戳一戳事件监听
                if ("notice".equals(postType) && "notify".equals(root.path("notice_type").asText()) && "poke".equals(root.path("sub_type").asText())) {
                    String time = root.path("time").asText();
                    String groupId = root.path("group_id").asText();
                    String userId = root.path("user_id").asText();
                    String selfId = root.path("self_id").asText();
                    String targetId = root.path("target_id").asText();

                    if (!Objects.equals(userId, selfId)) {
                        NapcatPokedEvent event = new NapcatPokedEvent(time, selfId, targetId, userId, groupId);
                        EventManager.getInstance().callEvent(event);
                    }
                }
                // 撤回事件监听
                if ("notice".equals(postType) && (("group_recall".equals(root.path("notice_type").asText()) && Config.getInstance().getNapcatMessageSpyGroups().contains(root.path("group_id").asText())) || "friend_recall".equals(root.path("notice_type").asText()))) {
                    String time = root.path("time").asText();
                    String groupId = root.path("group_id").asText();
                    String userId = root.path("user_id").asText();
                    String selfId = root.path("self_id").asText();
                    String messageId = root.path("message_id").asText();
                    String operatorId = root.path("operator_id").asText();
                    String noticeType = root.path("notice_type").asText();

                    NapcatRecallMessageEvent event = new NapcatRecallMessageEvent(time, selfId, groupId, userId, operatorId, messageId, noticeType);
                    EventManager.getInstance().callEvent(event);
                }

            } catch (Exception e) {
                log.error("JSON 解析或处理异常：{}", e.getMessage());
            }
        }
        return "{\"status\":\"ok\"}";
    }
}