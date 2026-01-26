package top.yzljc.qqbot.botkits.findinfo;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.CheckType;
import top.yzljc.qqbot.botkits.request.PostDataRequest;

public class GetUserName {
    public static String getUserName(long userId) {
        JsonNode json = PostDataRequest.getSimplePostResult(CheckType.GET_USER_INFO, userId);

        if (json != null) {
            return json.path("data").path("nick").asText();
        }
        return null;
    }
}