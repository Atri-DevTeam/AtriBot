package top.yzljc.atribot.chat.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.service.request.RequestType;
import top.yzljc.atribot.service.request.PostRequest;
import top.yzljc.atribot.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class GroupInformation {
    private static final Logger log = LoggerFactory.getLogger(GroupInformation.class);
    private static final boolean isDebugMode = Config.getInstance().isDebugMode();
    private static final long debugGroupId = Config.getInstance().getNapcatDebugGroupUin();

    public static String getGroupName(long groupId) {
        JsonNode json = PostRequest.getSimplePostResult(RequestType.GET_GROUP_INFO, "group_id", groupId);

        if (json != null) {
            return json.path("data").path("group_name").asText();
        }
        return String.valueOf(groupId);
    }

    public static Set<Long> fetchAllGroupIds() {
        Set<Long> groupIds = new HashSet<>();
        try {
            JsonNode resp = PostRequest.getPostResult(RequestType.GET_GROUP_LIST);

            if (resp != null && resp.has("data") && resp.get("data").isArray()) {
                for (JsonNode group : resp.get("data")) {
                    if (group.has("group_id")) {
                        try {
                            long gid = group.get("group_id").asLong();
                            groupIds.add(gid);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取群列表异常: {}", e.getMessage());
        }

        log.info("接到群号获取请求，共获取到 {} 个群聊。", groupIds.size());

        if (isDebugMode) {
            log.info("Debug 模式已开启，仅返回调试群号: {}", debugGroupId);
            Set<Long> debugSet = new HashSet<>();
            debugSet.add(debugGroupId);
            return debugSet;
        }
        return groupIds;
    }
}