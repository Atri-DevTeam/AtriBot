package top.yzljc.qqbot.botservice.message;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botservice.request.RequestType;
import top.yzljc.qqbot.botservice.request.PostRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botservice.userinfo.GetGroupInfo;
import top.yzljc.qqbot.botservice.userinfo.GetUserInfo;
import top.yzljc.qqbot.config.Config;

public class MessageFilter {

    private static final Logger log = LoggerFactory.getLogger(MessageFilter.class);

    public static void checkAndRecall(JsonNode json) {
        if (json == null) return;

        if (!json.has("message_type") || !"group".equals(json.path("message_type").asText())) {
            return;
        }

        String rawMessage = json.path("raw_message").asText();
        long messageId = json.path("message_id").asLong();
        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();

        if (rawMessage == null || rawMessage.isEmpty() || messageId == 0) {
            return;
        }

        if (SensitiveWordFilter.containsSensitiveWord(rawMessage)) {
            if (groupId == Config.getInstance().getDebugGroupId()) return;
            recallMessageSilent(messageId);

            String detectedWord = SensitiveWordFilter.findSensitiveWord(rawMessage);
            log.info("检测到违规词：{}, 已尝试撤回, 来自 QQ: {}, 消息 ID: {}, 群组: {}", detectedWord, GetUserInfo.getUserName(userId), messageId, GetGroupInfo.getGroupName(groupId));
            
	    }
    }

    private static void recallMessageSilent(long messageId) {
        PostRequest.sendSimplePost(RequestType.RECALL_MESSAGE,"message_id", messageId);
    }
}
