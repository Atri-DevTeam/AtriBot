package top.yzljc.atribot.chat.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;

import java.util.HashMap;
import java.util.Map;

public class UserInformation {
    private static String cachedBotId = null;
    private static final Map<String, String> cache = new HashMap<>();
    private static final Map<String, Map<String, Boolean>> groupAdminCache = new HashMap<>();

    public static String getBotId() {
        if (cachedBotId == null) {
            cachedBotId = specBotIdInternal();
        }
        return cachedBotId;
    }

    public static String getUserName(String userId) {
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        JsonNode json = PostRequest.getSimplePostResult(RequestType.GET_USER_INFO, "user_id", userId);

        if (json != null) {
            String name = json.path("data").path("nick").asText();
            cache.put(userId, name);
            return name;
        }
        return null;
    }

    public static boolean isGroupAdmin(String groupId, String userId) {
        if (groupAdminCache.containsKey(groupId) && groupAdminCache.get(groupId).containsKey(userId)) {
            return groupAdminCache.get(groupId).get(userId);
        }
        JsonNode json = PostRequest.getPostResult(RequestType.GET_GROUP_MEMBER_INFO, Map.of("group_id", groupId, "user_id", userId));
        if (json != null) {
            String role = json.path("data").path("role").asText();
            boolean isAdmin = role.equalsIgnoreCase("owner") || role.equalsIgnoreCase("admin");
            groupAdminCache.computeIfAbsent(groupId, k -> new HashMap<>()).put(userId, isAdmin);
            return isAdmin;
        }
        return false;
    }

    public static void clearCache() {
        cachedBotId = null;
    }

    private static String specBotIdInternal() {
        JsonNode loginInfo = PostRequest.getPostResult(RequestType.GET_LOGIN_INFO);
        if (loginInfo != null && loginInfo.has("data") && loginInfo.get("data").has("user_id")) {
            return loginInfo.get("data").get("user_id").asText();
        }
        return null;
    }
}
