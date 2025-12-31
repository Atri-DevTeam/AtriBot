package top.yzljc.qqbot.messages;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.tools.AutoRepeat;
import top.yzljc.qqbot.command.AnnounceGroup;
import top.yzljc.qqbot.utils.MessageStats;
import top.yzljc.qqbot.command.RollbackMessages;
import top.yzljc.qqbot.img.ManosabaDate;
import top.yzljc.qqbot.minecraft.SendCommand;
import top.yzljc.qqbot.news.HypixelNews;
import top.yzljc.qqbot.news.MinecraftNews;
import top.yzljc.qqbot.tools.AnnoyUser;
import top.yzljc.qqbot.utils.AutoAccept;
import top.yzljc.qqbot.utils.AutoSign;
import top.yzljc.qqbot.tools.CheckBilibili;
import top.yzljc.qqbot.tools.ElectricCheck;
import top.yzljc.qqbot.tools.LikeUser;
import top.yzljc.qqbot.command.SearchRelevant;

public class MessageProcessor {

    public static void processMessage(JsonNode json) {
        String postType = json.path("post_type").asText("");

        // 处理加群/好友请求
        if ("request".equals(postType)) {
            AutoAccept.handle(json);
            return;
        }

        // 只处理消息类型
        if (!"message".equals(postType)) {
            return;
        }

        // ==== 各类功能模块分发 ====
        ElectricCheck.processElectric(json);
        AutoSign.processAutoSign(json);
        AutoRepeat.processGroupMessage(json);
        ManosabaDate.processManodate(json);
        HypixelNews.processTestForHyp(json);
        AnnounceGroup.processAcCommand(json);
        AnnoyUser.processMessage(json);
        LikeUser.processCommand(json);
        RecordGroupMessage.processRecord(json);
        RollbackMessages.processCommand(json);
        MessageStats.processCommand(json);
        SearchRelevant.processCommand(json);
        GroupMessageFilter.checkAndRecall(json);

        String messageType = json.path("message_type").asText();
        if (!"group".equals(messageType)) {
            return;
        }

        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();
        String rawMessage = json.path("raw_message").asText();

        if (rawMessage != null) {
            String rawTrimmed = rawMessage.trim();

            // 尝试交给 MinecraftNews 处理指令
            if (MinecraftNews.processCommand(userId, groupId, rawTrimmed)) {
                return;
            }

            // 处理 /rc 指令 (Remote Control)
            if (rawTrimmed.startsWith("/rc")) {
                // 委托给 SendCommand 处理
                SendCommand.handle(userId, groupId, rawTrimmed);
            }
        }

        CheckBilibili.process(json);
    }
}