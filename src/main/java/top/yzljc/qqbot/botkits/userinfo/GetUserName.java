package top.yzljc.qqbot.botkits.userinfo;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;

public class GetUserName {
    public static String getUserName(long userId) {
        JsonNode json = PostRequest.getSimplePostResult(RequestType.GET_USER_INFO, "user_id", userId);

        if (json != null) {
            return json.path("data").path("nick").asText();
        }
        return null;
    }
}