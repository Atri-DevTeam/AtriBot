package top.yzljc.atricore.chat.napcat;

import top.yzljc.atricore.AtriProvider;
import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atricore.platform.napcat.RequestType;
import top.yzljc.atricore.platform.napcat.PostRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Napcat 群资料与成员信息查询
 *
 * @Author YZ_Ljc_
 * @ClassName GroupInformation
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.napcat
 */
public class GroupInformation {
    private static final Logger log = LoggerFactory.getLogger(GroupInformation.class);

    public static String getGroupName(String groupId) {
        JsonNode json = PostRequest.getSimplePostResult(RequestType.GET_GROUP_INFO, "group_id", groupId);

        if (json != null) {
            return json.path("data").path("group_name").asText();
        }
        return groupId;
    }

    public static Set<String> fetchAllGroupIds() {
        Set<String> groupIds = new HashSet<>();
        try {
            JsonNode resp = PostRequest.getPostResult(RequestType.GET_GROUP_LIST);

            if (resp != null && resp.has("data") && resp.get("data").isArray()) {
                for (JsonNode group : resp.get("data")) {
                    if (group.has("group_id")) {
                        try {
                            String gid = group.get("group_id").asText();
                            groupIds.add(gid);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取群列表异常: {}", e.getMessage());
        }

        log.info("接到群号获取请求，共获取到 {} 个群聊。", groupIds.size());

        if (AtriProvider.getInstance().getSettings().isDebugMode()) {
            log.info("Debug 模式已开启，仅返回调试群号: {}", AtriProvider.getInstance().getSettings().getNapcatDebugGroupUin());
            Set<String> debugSet = new HashSet<>();
            debugSet.add(AtriProvider.getInstance().getSettings().getNapcatDebugGroupUin());
            return debugSet;
        }
        return groupIds;
    }
}
