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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindRecall {
    static Settings settings = Config.getInstance();
    private static final long DEBUG_GROUP_ID = settings.getDebugGroupId();
    private static final long BOT_ID = settings.getBotUid();
    private static final List<Long> IGNORE_USER_IDS = settings.getIgnoredUsers();

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

        if (IGNORE_USER_IDS.contains(userId)) {
            return; // 忽略机器人的撤回
        }

        if (foundMessage != null) {
            TextImage.Result findResult = TextImage.parseTextImage(foundMessage);
            String myMessage = "[" + formattedTime + "] " + foundUserName + "在群 " + foundGroupName + " 撤回了一条消息：";

            if (findResult.textMessage() != null && findResult.imgBase64() == null){
                MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage + findResult.textMessage());
                return;
            }

            Pattern imgPattern = Pattern.compile("\\[CQ:image,([^]]+)]");
            Matcher stillHaveImg;
            int imgCount = 1;
            if (findResult.textMessage() == null) {
                MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage, findResult.imgBase64());
                return;
            }
            stillHaveImg = imgPattern.matcher(findResult.textMessage());
            MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage + findResult.textMessage().replaceAll("\\[CQ:image,([^]]+)]","") + " [图片 " + imgCount++ + "]", findResult.imgBase64());
            if (stillHaveImg.find()) {
                TextImage.Result newFindResult = TextImage.parseTextImage(findResult.textMessage());
                MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage + newFindResult.textMessage().replaceAll("\\[CQ:image,([^]]+)]","") + " [图片 " + imgCount + "]", newFindResult.imgBase64());
                while (true){
                    imgCount++;
                    stillHaveImg = imgPattern.matcher(newFindResult.textMessage());
                    if (!stillHaveImg.find()) {
                        return;
                    }
                    newFindResult = TextImage.parseTextImage(newFindResult.textMessage());
                    MessageSender.sendGroupMessage(DEBUG_GROUP_ID, myMessage + newFindResult.textMessage().replaceAll("\\[CQ:image,([^]]+)]","") + " [图片: " + imgCount + "]", newFindResult.imgBase64());
                }
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
