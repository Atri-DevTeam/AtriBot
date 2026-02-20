package top.yzljc.qqbot.botkits.userinfo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.config.ConfigFile;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GetFriendList {
    private static final Logger log = LoggerFactory.getLogger(GetFriendList.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String RECORD_FILE = ConfigFile.FRIEND_LIST.getFileName();
    private static final Map<Long, String> friendList = new ConcurrentHashMap<>();

    static {
        loadList();
    }

    public static boolean isFriend(long userId) {
        return friendList.containsKey(userId);
    }

    public static String getFriendNickname(long userId) {
        return friendList.get(userId);
    }

    public static void updateFriendList() {
        JsonNode result = PostRequest.getPostResult(RequestType.GET_FRIEND_LIST);

        if (result != null && result.has("data")) {
            JsonNode dataArray = result.get("data");
            if (dataArray.isArray()) {
                friendList.clear();

                for (JsonNode friendNode : dataArray) {
                    long userId = friendNode.get("user_id").asLong();
                    String nickname = friendNode.has("nickname") ? friendNode.get("nickname").asText() : "";

                    friendList.put(userId, nickname);
                }
                saveList();
                log.info("好友列表更新完成，共 {} 位好友", friendList.size());
            }
        } else {
            log.warn("获取好友列表失败或数据为空");
        }
    }

    private static void loadList() {
        File file = new File(RECORD_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, String> raw = mapper.readValue(reader, new TypeReference<>() {});

                friendList.clear();
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    friendList.put(Long.parseLong(entry.getKey()), entry.getValue());
                }
            } catch (Exception e) {
                log.error("加载好友列表文件失败: {}", e.getMessage());
            }
        }
    }

    private static void saveList() {
        Map<String, String> raw = new HashMap<>();
        for (Map.Entry<Long, String> entry : friendList.entrySet()) {
            raw.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        try (FileWriter writer = new FileWriter(RECORD_FILE, false)) {
            mapper.writeValue(writer, raw);
        } catch (Exception e) {
            log.warn("记录当前好友列表错误：{}", e.getMessage());
        }
    }
}