package top.yzljc.qqbot.botkits.findinfo;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.CheckType;
import top.yzljc.qqbot.botkits.request.PostDataRequest;

public class GetGroupName {
    public static String getGroupName(long groupId) {
        JsonNode json = PostDataRequest.getSimplePostResult(CheckType.GET_GROUP_INFO, groupId);

        if (json != null) {
            return json.path("data").path("group_name").asText();
        }
        return null;
    }
}