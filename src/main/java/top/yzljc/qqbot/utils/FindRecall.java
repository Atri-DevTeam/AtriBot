package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.image.TextImage;
import top.yzljc.qqbot.botkits.message.MessageRecorder;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.findinfo.GetGroupName;
import top.yzljc.qqbot.botkits.findinfo.GetUserName;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FindRecall {
    static Settings settings = Config.getInstance();
    private static final long DEBUG_GROUP_ID = settings.getDebugGroupId();
    private static final long BOT_ID = settings.getBotUid();

    public static void processMessage(JsonNode json) {

        long messageId = json.path("message_id").asLong();
        long groupId = json.path("group_id").asLong();
        long userId = json.path("user_id").asLong();
        long time = json.path("time").asLong();

        String formattedTime = formatTimestamp(time);
        String foundMessage = MessageRecorder.searchMessage(groupId, messageId);
        String foundUserName = GetUserName.getUserName(userId);
        String foundGroupName = GetGroupName.getGroupName(groupId);

        if (userId == BOT_ID) {
            return; // 不写会炸，别问我怎么知道的
        }

        if (userId == 3889056552L){
            return; // 忽略某个机器人的撤回
        }

        if (foundMessage != null) {
            TextImage.Result findResult = TextImage.parseTextImage(foundMessage);
            String myMessage = "[" + formattedTime + "] " + foundUserName + "在群 " + foundGroupName + " 撤回了一条消息：";

            if (findResult.textMessage() != null && findResult.imgBase64() == null){
                MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage + findResult.textMessage());
            }
            else if (findResult.imgBase64() != null && findResult.textMessage() == null){
                MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage, findResult.imgBase64());
            }
            else {
                MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage + findResult.textMessage(), findResult.imgBase64());
            }
        }
    }

    public static String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

}
