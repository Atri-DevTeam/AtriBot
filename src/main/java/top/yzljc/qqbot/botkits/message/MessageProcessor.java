package top.yzljc.qqbot.botkits.message;

import top.yzljc.qqbot.command.*;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.groups.GroupModeManager;
import top.yzljc.qqbot.debug.PacketEvent;
import top.yzljc.qqbot.feature.*;
import top.yzljc.qqbot.deprecated.ServerStatusReport;
import top.yzljc.qqbot.feature.HappyNewYear;
import top.yzljc.qqbot.feature.minecraft.McNetworkInfo;
import top.yzljc.qqbot.feature.minecraft.MojangStatus;
import top.yzljc.qqbot.utils.CommandHelp;
import top.yzljc.qqbot.utils.MessageStats;
import top.yzljc.qqbot.feature.ManosabaDate;
import top.yzljc.qqbot.feature.minecraft.SendCommand;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.utils.AutoAccept;
import top.yzljc.qqbot.feature.AutoSign;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessor.class);

    public static void processMessage(JsonNode json) {
        String postType = json.path("post_type").asText("");

        PacketEvent.process(json);
        SendPoke.process(json);

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

        // 复读机消息拦截
        if (GroupConfigManager.isFeatureEnabled(groupId, "repeat_msg")) {
            AutoRepeat.processGroupMessage(json);
        }

        if (rawMessage != null) {
            String rawTrimmed = rawMessage.trim();

            if (MinecraftNews.processCommand(userId, groupId, rawTrimmed)) {
                return;
            }

            if (rawTrimmed.startsWith("/rc")) {
                SendCommand.handle(userId, groupId, rawTrimmed);
            }

            if (groupId == 715842297L && rawTrimmed.startsWith("/wl")){
                if (userId != 1981868489L && userId != 3199590352L) {
                    return;
                }
                SendCommand.handleWhiteListCommand(groupId, rawTrimmed);
            }
        }
    }
}
