package top.yzljc.qqbot.messages;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.command.*;
import top.yzljc.qqbot.debug.PacketEvent;
import top.yzljc.qqbot.gordonhim.ServerStatusReport;
import top.yzljc.qqbot.img.HappyNewYear;
import top.yzljc.qqbot.minecraft.McNetworkInfo;
import top.yzljc.qqbot.minecraft.MojangStatus;
import top.yzljc.qqbot.tools.*;
import top.yzljc.qqbot.utils.CommandHelp;
import top.yzljc.qqbot.utils.MessageStats;
import top.yzljc.qqbot.img.ManosabaDate;
import top.yzljc.qqbot.minecraft.SendCommand;
import top.yzljc.qqbot.news.HypixelNews;
import top.yzljc.qqbot.news.MinecraftNews;
import top.yzljc.qqbot.utils.AutoAccept;
import top.yzljc.qqbot.utils.AutoSign;

public class MessageProcessor {

    public static void processMessage(JsonNode json) {
        String postType = json.path("post_type").asText("");

        PacketEvent.process(json);
        PokeGift.process(json);

        // 处理加群/好友请求
        if ("request".equals(postType)) {
            AutoAccept.handle(json);
            return;
        }

        // 只处理消息类型
        if (!"message".equals(postType)) {
            return;
        }

        GroupModeManager.process(json);
        ElectricCheck.processElectric(json);
        AutoSign.processAutoSign(json);
        AutoRepeat.processGroupMessage(json);
        ManosabaDate.processManodate(json);
        HypixelNews.processTestForHyp(json);
        AnnoyUser.processMessage(json);
        LikeUser.processCommand(json);
        RecordGroupMessage.processRecord(json);
        RollbackMessages.processCommand(json);
        MessageStats.processCommand(json);
        SearchRelevant.processCommand(json);
        GroupMessageFilter.checkAndRecall(json);
        ServerStatusReport.process(json);
        MojangStatus.process(json);
        CheckBilibili.process(json);
        HappyNewYear.processManodate(json);
        Reboot.process(json);
        Hitokoto.process(json);
        CommandHelp.process(json);
        McNetworkInfo.process(json);
        SendCommand.processMessage(json);

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
    }
}
