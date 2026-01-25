package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.message.MessageRecorder;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.seizeinfo.GetGroupName;
import top.yzljc.qqbot.botkits.seizeinfo.GetUserName;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FindRecall {
    static Settings settings = Config.getInstance();
    private static final long DEBUG_GROUP_ID = settings.getDebugGroupId();

    public static void processMessage(JsonNode json) {

        long messageId = json.path("message_id").asLong();
        long groupId = json.path("group_id").asLong();
        long userId = json.path("user_id").asLong();
        long time = json.path("time").asLong();

        String formattedTime = formatTimestamp(time);
        String foundMessage = MessageRecorder.searchMessage(groupId, messageId);
        String foundUserName = GetUserName.getUserName(userId);
        String foundGroupName = GetGroupName.getGroupName(groupId);
        String myMessage = null;

        if (foundMessage != null) {
            myMessage = "[" + formattedTime + "] " + foundUserName + "在群 " + foundGroupName + " 撤回了一条消息：" + foundMessage;
        }
        MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage);
    }

    public static String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

}
