package top.yzljc.qqbot.config.groups;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class GroupList {
    private static final Logger log = LoggerFactory.getLogger(GroupList.class);

    static Settings settings = Config.getInstance();
    private static final boolean isDebugMode = settings.isDebugMode();
    private static final long debugGroupId = settings.getDebugGroupId();

    public static Set<Long> fetchAllGroupIds() {
        Set<Long> groupIds = new HashSet<>();

        log.info("接到群号获取请求，开始同步群列表……");

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

        log.info("同步完成，共获取到 {} 个群聊。", groupIds.size());

        if (isDebugMode) {
            log.info("Debug 模式已开启，仅返回调试群号: {}", debugGroupId);
            Set<Long> debugSet = new HashSet<>();
            debugSet.add(debugGroupId);
            return debugSet;
        }

        return groupIds;
    }
}