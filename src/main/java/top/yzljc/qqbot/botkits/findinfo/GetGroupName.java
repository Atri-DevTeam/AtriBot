package top.yzljc.qqbot.botkits.findinfo;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;

public class GetGroupName {
    public static String getGroupName(long groupId) {
        JsonNode json = PostRequest.getSimplePostResult(RequestType.GET_GROUP_INFO, "group_id", groupId);

        if (json != null) {
            return json.path("data").path("group_name").asText();
        }
        return null;
    }
}