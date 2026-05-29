package top.yzljc.atribot.chat.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.service.request.PostRequest;
import top.yzljc.atribot.service.request.RequestType;

import java.util.HashMap;
import java.util.Map;

public class UserInformation {
    private static Long cachedBotId = null;
    private static final Map<Long, String> cache = new HashMap<>();
    private static final Map<Long, Map<Long, Boolean>> groupAdminCache = new HashMap<>();

    public static long getBotId() {
        if (cachedBotId == null) {
            cachedBotId = specBotIdInternal();
        }
        return cachedBotId;
    }

    public static String getUserName(long userId) {
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

    public static boolean isGroupAdmin(long groupId, long userId) {
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

    private static long specBotIdInternal() {
        JsonNode loginInfo = PostRequest.getPostResult(RequestType.GET_LOGIN_INFO);
        if (loginInfo != null && loginInfo.has("data") && loginInfo.get("data").has("user_id")) {
            return loginInfo.get("data").get("user_id").asLong();
        }
        return 0L; // 没找着
    }
}