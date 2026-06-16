package top.yzljc.atribot.chat.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FriendList {
    private static final Logger log = LoggerFactory.getLogger(FriendList.class);
    private static final Map<String, String> friendList = new ConcurrentHashMap<>();

    static {
        updateFriendList();
    }

    public static boolean isFriend(String userId) {
        return friendList.containsKey(userId);
    }

    public static String getFriendNickname(String userId) {
        return friendList.get(userId);
    }

    public static int getFriendCount() {
        return friendList.size();
    }

    public static void updateFriendList() {
        JsonNode result = PostRequest.getPostResult(RequestType.GET_FRIEND_LIST);

        if (result != null && result.has("data")) {
            JsonNode dataArray = result.get("data");
            if (dataArray.isArray()) {
                friendList.clear();

                for (JsonNode friendNode : dataArray) {
                    String userId = friendNode.get("user_id").asText();
                    String nickname = friendNode.has("nickname") ? friendNode.get("nickname").asText() : "";

                    friendList.put(userId, nickname);
                }
                log.info("好友列表更新完成，共 {} 位好友", friendList.size());
            }
        } else {
            log.warn("获取好友列表失败或数据为空");
        }
    }
}
