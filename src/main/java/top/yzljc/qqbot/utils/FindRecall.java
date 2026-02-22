package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.userinfo.GetUserInfo;
import top.yzljc.qqbot.botkits.message.MessageRecorder;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.userinfo.GetGroupInfo;
import top.yzljc.qqbot.botkits.tools.FT;
import top.yzljc.qqbot.botkits.tools.MM;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FindRecall {
    static Settings settings = Config.getInstance();
    private static final long DEBUG_GROUP_ID = settings.getDebugGroupId();
    private static final List<Long> IGNORE_USER_IDS = settings.getIgnoredUsers();

    public static void processMessage(JsonNode json) {

        long messageId = json.path("message_id").asLong();
        long groupId = json.path("group_id").asLong();
        long userId = json.path("user_id").asLong();
        long time = json.path("time").asLong();

        String formattedTime = formatTimestamp(time);
        String foundMessage = FT.unescape(MessageRecorder.searchMessage(groupId, messageId));
        String foundUserName = GetUserInfo.getUserName(userId);
        String foundGroupName = GetGroupInfo.getGroupName(groupId);

        if (userId == GetUserInfo.getBotId()) {
            return; // 不写会炸，别问我怎么知道的
        }

        if (IGNORE_USER_IDS.contains(userId)) {
            return; // 忽略机器人的撤回
        }
        String myMessage = "[" + formattedTime + "] " + foundUserName + "在群 " + foundGroupName + " 撤回了一条消息：";
        Map<String,Object> finalMsg = new HashMap<>();
        finalMsg.put("type", "text");
        finalMsg.put("data", Map.of("text", myMessage));
        List<Map<String, Object>> spyMessage = MM.parse(foundMessage);
        spyMessage.addFirst(finalMsg);

        MessageSender.sendGroupData(DEBUG_GROUP_ID,spyMessage);

    }

    public static String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
