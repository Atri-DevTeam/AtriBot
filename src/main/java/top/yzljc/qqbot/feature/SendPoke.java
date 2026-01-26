package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

public class SendPoke {

    private static final Logger log = LoggerFactory.getLogger(SendPoke.class);

    static Settings settings = Config.getInstance();
    private static final long botQq = settings.getBotUid();

    public static void process(JsonNode json) {
        String postType = json.path("post_type").asText("");
        if (!"notice".equals(postType)) {
            return;
        }

        String noticeType = json.path("notice_type").asText("");
        String subType = json.path("sub_type").asText("");

        if ("notify".equals(noticeType) && "poke".equals(subType)) {
            long targetId = json.path("target_id").asLong();

            if (targetId == botQq) {
                long groupId = json.path("group_id").asLong();
                long userId = json.path("user_id").asLong(); // 戳的入

                if (!GroupConfigManager.isFeatureEnabled(groupId,"send_poke")) {
                    return;
                }

                if (userId == botQq) return;

                log.info("监测到用户 {} 在群 {} 戳了机器人，准备反击！", userId, groupId);
                
                PostRequest.sendPoke(groupId, userId);
            }
        }
    }
}
