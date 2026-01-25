package top.yzljc.qqbot.botkits.message;

import top.yzljc.qqbot.command.*;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.groups.GroupModeManager;
import top.yzljc.qqbot.debug.PacketEvent;
import top.yzljc.qqbot.feature.*;
import top.yzljc.qqbot.deprecated.ServerStatusReport;
import top.yzljc.qqbot.feature.HappyNewYear;
import top.yzljc.qqbot.feature.minecraft.specificserver.HBTPlayerData;
import top.yzljc.qqbot.feature.minecraft.MojangStatus;
import top.yzljc.qqbot.utils.CommandHelp;
import top.yzljc.qqbot.utils.FindRecall;
import top.yzljc.qqbot.utils.MessageStats;
import top.yzljc.qqbot.feature.ManosabaDate;
import top.yzljc.qqbot.feature.minecraft.ServerRcon;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.utils.AutoAccept;
import top.yzljc.qqbot.feature.AutoSign;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class MessageProcessor {

    static Settings settings = Config.getInstance();
    private static final List<Long> spyGroups = settings.getMessageSpyGroups();

    public static void processMessage(JsonNode json) {
        String postType = json.path("post_type").asText("");
        String messageType = json.path("message_type").asText();
        String noticeType = json.path("notice_type").asText("");
        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();
        String rawMessage = json.path("raw_message").asText();

        // 这俩是爹不能放后面
        PacketEvent.process(json);
        SendPoke.process(json);

        // 撤回内容消息上报处理
        if (spyGroups.contains(groupId) && "notice".equals(postType) && "group_recall".equals(noticeType)){
            FindRecall.processMessage(json);
        }

        // 处理加群/好友请求
        if ("request".equals(postType)) {
            AutoAccept.handle(json);
            return;
        }

        // 下面的内容都只处理消息类型
        if (!"message".equals(postType) && !"group".equals(messageType)) {
            return;
        }

        GroupModeManager.process(json);
        ElectricCheck.processElectric(json);
        AutoSign.processAutoSign(json);
        ManosabaDate.processManodate(json);
        HypixelNews.processTestForHyp(json);
        AnnoyUser.processMessage(json);
        LikeUser.processCommand(json);
        MessageRecorder.processRecord(json);
        RollbackMessages.processCommand(json);
        MessageStats.processCommand(json);
        SearchRelevant.processCommand(json);
        ServerStatusReport.process(json);
        MojangStatus.process(json);
        CheckBilibili.process(json);
        HappyNewYear.processManodate(json);
        Reboot.process(json);
        Hitokoto.process(json);
        CommandHelp.process(json);
        HBTPlayerData.process(json);
        ServerRcon.processMessage(json);

        // 不是我管的群我查个集贸，浪费资源
        if (spyGroups.contains(groupId)) {
            MessageFilter.checkAndRecall(json);
        }

        // 复读机消息拦截
        if (GroupConfigManager.isFeatureEnabled(groupId, "repeat_msg")) {
            AutoRepeat.processGroupMessage(json);
        }

        if (rawMessage != null) {
            String rawTrimmed = rawMessage.trim();

            if (MinecraftNews.processCommand(userId, groupId, rawTrimmed)) {
                return;
            }
        }
    }
}
