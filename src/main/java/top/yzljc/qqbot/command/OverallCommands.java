package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.feature.*;
import top.yzljc.qqbot.feature.github.WebhookServer;
import top.yzljc.qqbot.feature.minecraft.MojangStatus;
import top.yzljc.qqbot.feature.minecraft.Motd;
import top.yzljc.qqbot.feature.minecraft.ServerStatus;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.utils.CommandHelp;
import top.yzljc.qqbot.utils.MessageStats;

import java.util.List;

public class OverallCommands {
    private static final Logger log = LoggerFactory.getLogger(OverallCommands.class);
    static Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();
    private static final long ManosabaGroup = settings.getManosabaGroupId();
    private static final String[] KEYWORDS_HITOKOTO = settings.getKeywordsHitokoto();
    private static final String[] KEYWORDS_LIKE_USER = settings.getKeywordsLikeUser();
    private static final String[] KEYWORDS_ELECTRIC = {"电表", "dianbiao", "db"};
    private static final long BOT_QQ = settings.getBotUid();

    public static void processCommand(JsonNode json) {
        // 二次校验，虽然意义不大
        String postType = json.path("post_type").asText("");
        String messageType = json.path("message_type").asText("");
        if (!"message".equals(postType) || !"group".equals(messageType)){
            return;
        }

        String rawMessage = json.path("raw_message").asText("").trim();
        long groupId = json.path("group_id").asLong();
        long userId = json.path("user_id").asLong();

        if ("/manodate".equals(rawMessage) && groupId == ManosabaGroup){
            ManosabaDate.receiveManodate(groupId);
        }
        if ("/checkmcnews".equals(rawMessage) && admins.contains(userId)){
            MinecraftNews.processUpdate(groupId);
        }
        if ("/checkhypnews".equals(rawMessage) && admins.contains(userId)){
            HypixelNews.processTestForHyp(groupId);
        }
        if (hitokotoKeyword(rawMessage) && GroupConfigManager.isFeatureEnabled(groupId, "one_text")){
            Hitokoto.processHitokoto(groupId);
        }
        if (admins.contains(userId) && "/reboot".equalsIgnoreCase(rawMessage)) {
            Reboot.processReboot(userId, groupId);
        }
        if ("/happynewyear".equals(rawMessage)) {
            HappyNewYear.processHappyNewYear(groupId);
        }
        if ("/mojang".equalsIgnoreCase(rawMessage) && GroupConfigManager.isFeatureEnabled(groupId,"mojang_status")) {
            MojangStatus.processCheckMojangStatus(groupId);
        }
        if (rawMessage.startsWith("/motd") && GroupConfigManager.isFeatureEnabled(groupId, "motd")) {
            Motd.processCommand(groupId, rawMessage);
        }
        if (rawMessage.contains("[CQ:at,qq=" + BOT_QQ + "]") && rawMessage.toLowerCase().contains("/help")){
            CommandHelp.processHelp(groupId);
        }
        if (likeUserKeyword(rawMessage) && GroupConfigManager.isFeatureEnabled(groupId,"like_user")){
            LikeUser.processCommand(userId, groupId);
        }
        if (rawMessage.startsWith("/rollback") && admins.contains(userId)){
            RollbackMessages.processRollBack(groupId,rawMessage);
        }
        if (admins.contains(userId) && "/signall".equals(rawMessage)){
            AutoSign.processAutoSign();
        }
        if (rawMessage.startsWith("/github") && admins.contains(userId)){
            WebhookServer.processCommand(groupId,rawMessage);
        }
        if (rawMessage.startsWith("/stats")){
            MessageStats.processCommand(groupId,rawMessage);
        }
        if (rawMessage.startsWith("/serverstatus")) {
            ServerStatus.processModeChange(userId,groupId,rawMessage);
        }
        if (electricKeyword(rawMessage)){
            if (!admins.contains(userId)){
                if (GroupConfigManager.isFeatureEnabled(groupId,"electric_check")) {
                    ElectricCheck.processElectric(groupId);
                }
            }else{
                ElectricCheck.processElectric(groupId);
            }
        }
    }

    private static boolean hitokotoKeyword(String msg) {
        for (String kw : KEYWORDS_HITOKOTO)
            if (msg.contains(kw)) return true;
        return false;
    }

    private static boolean likeUserKeyword(String msg) {
        for (String kw : KEYWORDS_LIKE_USER)
            if (msg.equalsIgnoreCase(kw)) return true;
        return false;
    }

    private static boolean electricKeyword(String msg) {
        for (String kw : KEYWORDS_ELECTRIC)
            if (msg.equalsIgnoreCase(kw)) return true;
        return false;
    }
}
