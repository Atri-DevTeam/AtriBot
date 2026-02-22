package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.userinfo.GetUserInfo;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

import java.util.HashMap;
import java.util.Map;

public class SendPoke {

    private static final Logger log = LoggerFactory.getLogger(SendPoke.class);
    private static final long BOT_QQ = GetUserInfo.getBotId();

    public static void process(JsonNode json) {
        String postType = json.path("post_type").asText("");
        if (!"notice".equals(postType)) {
            return;
        }

        String noticeType = json.path("notice_type").asText("");
        String subType = json.path("sub_type").asText("");

        if ("notify".equals(noticeType) && "poke".equals(subType)) {
            long targetId = json.path("target_id").asLong();

            if (targetId == BOT_QQ) {
                long groupId = json.path("group_id").asLong();
                long userId = json.path("user_id").asLong(); // 戳的入

                if (!GroupConfigManager.isFeatureEnabled(groupId,"send_poke")) {
                    return;
                }

                if (userId == BOT_QQ) return;

                log.info("监测到用户 {} 在群 {} 戳了机器人，准备反击！", userId, groupId);

                Map<String, Object> params = new HashMap<>();
                params.put("group_id", groupId);
                params.put("user_id", userId);

                PostRequest.sendPost(RequestType.GROUP_POKE, params);
                log.info("已向用户 {} 反戳！", userId);
            }
        }
    }
}
