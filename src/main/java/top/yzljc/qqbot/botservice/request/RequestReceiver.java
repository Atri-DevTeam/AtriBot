package top.yzljc.qqbot.botservice.request;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.event.EventManager;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.event.Sender;
import top.yzljc.qqbot.event.impl.*;
import top.yzljc.qqbot.utils.Logger;

@RestController
public class RequestReceiver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PostMapping("/")
    public String handleRequest(@RequestBody JsonNode root) {
        if (root != null) {
            try {
                String postType = root.path("post_type").asText("");
                // 群聊/私聊
                if ("message".equals(postType)) {
                    long messageId = root.path("message_id").asLong();
                    long time = root.path("time").asLong();
                    long selfId = root.path("self_id").asLong();
                    long userId = root.path("user_id").asLong();
                    String rawMessage = root.path("raw_message").asText();
                    String messageType = root.path("message_type").asText();

                    // 解析 Sender
                    JsonNode senderNode = root.path("sender");
                    Sender senderObj = null;
                    if (!senderNode.isMissingNode() && !senderNode.isNull()) {
                        senderObj = new Sender(
                                senderNode.path("user_id").asLong(),
                                senderNode.path("nickname").asText(""),
                                senderNode.path("card").asText(""),
                                senderNode.path("role").asText("")
                        );
                    }

                    java.util.LinkedList<MessageSegment> segmentList = MAPPER.convertValue(
                            root.path("message"),
                            new TypeReference<>() {
                            }
                    );

                    // 触发对应的事件
                    if ("group".equals(messageType)) {
                        long groupId = root.path("group_id").asLong();
                        GroupMessageEvent event = new GroupMessageEvent(messageId, groupId, userId, rawMessage, segmentList, time, selfId, senderObj);
                        EventManager.getInstance().callEvent(event);
                    } else if ("private".equals(messageType)) {
                        PrivateMessageEvent event = new PrivateMessageEvent(messageId, userId, rawMessage, segmentList, time, selfId, senderObj);
                        EventManager.getInstance().callEvent(event);
                    }
                }
                // 群成员变动事件
                if ("notice".equals(postType) && root.has("group_id") && !root.path("notice_type").asText().equals("group_recall")) {
                    long time = root.path("time").asLong();
                    long groupId = root.path("group_id").asLong();
                    long userId = root.path("user_id").asLong();
                    long selfId = root.path("self_id").asLong();
                    String noticeType = root.path("notice_type").asText();
                    long operatorId = root.path("operator_id").asLong();
                    String subType = root.path("sub_type").asText();
                    GroupMemberChangeEvent event = new GroupMemberChangeEvent(time, selfId, groupId, userId, operatorId, subType, noticeType);
                    EventManager.getInstance().callEvent(event);
                }

                // 群管理事件监听
                if ("request".equals(postType) && "group".equals(root.path("request_type").asText())) {
                    long time = root.path("time").asLong();
                    long groupId = root.path("group_id").asLong();
                    long userId = root.path("user_id").asLong();
                    long selfId = root.path("self_id").asLong();
                    String subType = root.path("sub_type").asText();
                    String flag = root.path("flag").asText();
                    String comment = root.path("comment").asText();
                    GroupRequestEvent event = new GroupRequestEvent(time, selfId, groupId, userId, flag, subType, comment);
                    EventManager.getInstance().callEvent(event);
                }
                // 加好友事件监听器
                if ("request".equals(postType) && "friend".equals(root.path("request_type").asText())) {
                    long time = root.path("time").asLong();
                    long userId = root.path("user_id").asLong();
                    long selfId = root.path("self_id").asLong();
                    String flag = root.path("flag").asText();

                    FriendRequestEvent event = new FriendRequestEvent(time, selfId, userId, flag);
                    EventManager.getInstance().callEvent(event);
                }
                // 戳一戳事件监听
                if ("notice".equals(postType) && "notify".equals(root.path("notice_type").asText()) && "poke".equals(root.path("sub_type").asText())) {
                    long time = root.path("time").asLong();
                    long groupId = root.path("group_id").asLong();
                    long userId = root.path("user_id").asLong();
                    long selfId = root.path("self_id").asLong();
                    long targetId = root.path("target_id").asLong();

                    if (userId != selfId) {
                        GetPokeEvent event = new GetPokeEvent(time, selfId, targetId, userId, groupId);
                        EventManager.getInstance().callEvent(event);
                    }
                }
                // 撤回事件监听
                if ("notice".equals(postType) && (("group_recall".equals(root.path("notice_type").asText()) && Config.getInstance().getMessageSpyGroups().contains(root.path("group_id").asLong())) || "friend_recall".equals(root.path("notice_type").asText()))) {
                    long time = root.path("time").asLong();
                    long groupId = root.path("group_id").asLong();
                    long userId = root.path("user_id").asLong();
                    long selfId = root.path("self_id").asLong();
                    long messageId = root.path("message_id").asLong();
                    long operatorId = root.path("operator_id").asLong();
                    String noticeType = root.path("notice_type").asText();

                    RecallMessageEvent event = new RecallMessageEvent(time, selfId, groupId, userId, operatorId, messageId, noticeType);
                    EventManager.getInstance().callEvent(event);
                }

            } catch (Exception e) {
                Logger.error("JSON 解析或处理异常：{}", e.getMessage());
            }
        }
        return "{\"status\":\"ok\"}";
    }
}