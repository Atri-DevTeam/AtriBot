package top.yzljc.qqbot.botkits.userinfo;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;

public class GetBotInfo {
    private static Long cachedBotId = null;

    public static long getBotId() {
        if (cachedBotId == null) {
            cachedBotId = specBotIdInternal();
        }
        return cachedBotId;
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