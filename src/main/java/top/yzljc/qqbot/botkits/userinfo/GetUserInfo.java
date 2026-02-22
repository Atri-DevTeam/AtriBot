package top.yzljc.qqbot.botkits.userinfo;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;

public class GetUserInfo {
    private static Long cachedBotId = null;

    public static long getBotId() {
        if (cachedBotId == null) {
            cachedBotId = specBotIdInternal();
        }
        return cachedBotId;
    }

    public static String getUserName(long userId) {
        JsonNode json = PostRequest.getSimplePostResult(RequestType.GET_USER_INFO, "user_id", userId);

        if (json != null) {
            return json.path("data").path("nick").asText();
        }
        return null;
    }

    public static void clearCache() {
        cachedBotId = null;
    }

    private static long specBotIdInternal() {
        JsonNode loginInfo = PostRequest.getPostResult(RequestType.GET_LOGIN_INFO);
        if (loginInfo != null && loginInfo.has("data") && loginInfo.get("data").has("user_id")) {
            return loginInfo.get("data").get("user_id").asLong();
        }
        return 0L; // 没找着
    }
}