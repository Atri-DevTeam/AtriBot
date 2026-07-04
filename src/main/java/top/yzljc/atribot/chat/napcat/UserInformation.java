package top.yzljc.atribot.chat.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserInformation {
    private static String napcatBotId = null;
    private static final Map<String, String> cache = new HashMap<>();
    private static final Map<String, Map<String, Boolean>> groupAdminCache = new HashMap<>();
    private static final List<BotUinInterval> botUinList = new ArrayList<>();

    public static String getBotId() {
        if (napcatBotId == null) {
            napcatBotId = specBotIdInternal();
        }
        return napcatBotId;
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
        napcatBotId = null;
    }

    private static String specBotIdInternal() {
        JsonNode loginInfo = PostRequest.getPostResult(RequestType.GET_LOGIN_INFO);
        if (loginInfo != null && loginInfo.has("data") && loginInfo.get("data").has("user_id")) {
            return loginInfo.get("data").get("user_id").asText();
        }
        return null;
    }

    public static boolean isBot(String userId) {
        if (botUinList.isEmpty()) {
            var data = PostRequest.getPostResult(RequestType.BOT_UIN_RANGE, Map.of());
            if (data != null && data.has("data") && data.get("data").isArray()) {
                for (JsonNode interval : data.get("data")) {
                    long min = interval.path("minUin").asLong();
                    long max = interval.path("maxUin").asLong();
                    botUinList.add(new BotUinInterval(min, max));
                }
            }
        }
        try {
            long uin = Long.parseLong(userId);
            for (BotUinInterval interval : botUinList) {
                if (uin >= interval.min() && uin <= interval.max()) {
                    return true;
                }
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public record BotUinInterval(long min, long max) {
    }
}
