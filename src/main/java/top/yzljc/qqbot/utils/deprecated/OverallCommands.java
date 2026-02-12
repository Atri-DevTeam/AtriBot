package top.yzljc.qqbot.utils.deprecated;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 计划移除
 */
@Deprecated(since = "2.6.0", forRemoval = true)
public class OverallCommands {
//    private static final Logger log = LoggerFactory.getLogger(OverallCommands.class);
//    static Settings settings = Config.getInstance();
//    private static final List<Long> admins = settings.getAdminUids();
//    private static final long ManosabaGroup = settings.getManosabaGroupId();
//    private static final String[] KEYWORDS_HITOKOTO = settings.getKeywordsHitokoto();
//    private static final String[] KEYWORDS_LIKE_USER = settings.getKeywordsLikeUser();
//    private static final String[] KEYWORDS_ELECTRIC = {"电表", "dianbiao", "db"};
//    private static final long BOT_QQ = GetBotInfo.getBotId();
//    private static final String COMMAND_PREFIX = settings.getCommandPrefix();

    public static void processCommand(JsonNode json) {
        // 二次校验，虽然意义不大
//        String postType = json.path("post_type").asText("");
//        String messageType = json.path("message_type").asText("");
//        if (!"message".equals(postType) || !"group".equals(messageType)){
//            return;
//        }
//
//        String rawMessage = json.path("raw_message").asText("").trim();
//        long groupId = json.path("group_id").asLong();
//        long userId = json.path("user_id").asLong();


//        if ((COMMAND_PREFIX + "manodate").equals(rawMessage) && groupId == ManosabaGroup){
//            ManosabaDate.receiveManodate(groupId);
//        }
//        if ((COMMAND_PREFIX + "checkmcnews").equals(rawMessage) && admins.contains(userId)){
//            MinecraftNews.processUpdate(groupId);
//        }
//        if ((COMMAND_PREFIX + "checkhypnews").equals(rawMessage) && admins.contains(userId)){
//            HypixelNews.processTestForHyp(groupId);
//        }
//        if (hitokotoKeyword(rawMessage) && GroupConfigManager.isFeatureEnabled(groupId, "one_text")){
//            Hitokoto.processHitokoto(groupId);
//        }
//        if (admins.contains(userId) && (COMMAND_PREFIX + "reboot").equalsIgnoreCase(rawMessage)) {
//            Reboot.processReboot(userId, groupId);
//        }
//        if ((COMMAND_PREFIX + "happynewyear").equals(rawMessage)) {
//            HappyNewYear.processHappyNewYear(groupId);
//        }
//        if ((COMMAND_PREFIX + "mojang").equalsIgnoreCase(rawMessage) && GroupConfigManager.isFeatureEnabled(groupId,"mojang_status")) {
//            MojangStatus.processCheckMojangStatus(groupId);
//        }
//        if (rawMessage.startsWith(COMMAND_PREFIX + "motd") && GroupConfigManager.isFeatureEnabled(groupId, "motd")) {
//            Motd.processCommand(groupId, rawMessage);
//        }
//        if (rawMessage.contains("[CQ:at,qq=" + BOT_QQ + "]") && rawMessage.toLowerCase().contains(COMMAND_PREFIX + "help")){
//            CommandHelp.processHelp(groupId);
//        }
//        if (likeUserKeyword(rawMessage) && GroupConfigManager.isFeatureEnabled(groupId,"like_user")){
//            LikeUser.processCommand(userId, groupId);
//        }
//        if (rawMessage.startsWith(COMMAND_PREFIX + "rollback") && admins.contains(userId)){
//            RollbackMessages.processRollBack(groupId,rawMessage);
//        }
//        if (admins.contains(userId) && (COMMAND_PREFIX + "signall").equals(rawMessage)){
//            AutoSign.processAutoSign();
//        }
//        if (admins.contains(userId) && rawMessage.startsWith(COMMAND_PREFIX + "recalllast")){
//            RecallLastMsg.recallLastMsg();
//        }
//        if (rawMessage.startsWith(COMMAND_PREFIX + "github") && admins.contains(userId)){
//            WebhookServer.processCommand(groupId,rawMessage);
//        }
//        if (rawMessage.startsWith(COMMAND_PREFIX + "stats")){
//            MessageStats.processCommand(groupId,rawMessage);
//        }
//        if (rawMessage.equals(COMMAND_PREFIX + "groupinfo")){
//            GroupConfigManager.getGroupStatusDescription(groupId);
//        }
//        if (rawMessage.startsWith(COMMAND_PREFIX + "serverstatus")) {
//            ServerStatus.processModeChange(userId, groupId, rawMessage);
//        }
//        if (rawMessage.equals(COMMAND_PREFIX + "wakeup") && admins.contains(userId)){
//            WakeUp.debugSendImgToGroup(groupId);
//        }
//        if (rawMessage.startsWith(COMMAND_PREFIX + "bc") && admins.contains(userId)){
//            String bcContent = rawMessage.substring(3).trim();
//            Broadcast.getBroadcastRequest(bcContent);
//        }
//        if (rawMessage.startsWith(COMMAND_PREFIX + "debugbc") && admins.contains(userId)){
//            String bcContent = rawMessage.substring(8).trim();
//            Broadcast.debugBroadcastRequest(bcContent);
//        }
//        if (electricKeyword(rawMessage)){
//            if (!admins.contains(userId)){
//                if (GroupConfigManager.isFeatureEnabled(groupId,"electric_check")) {
//                    ElectricCheck.processElectric(groupId);
//                }
//            }else{
//                ElectricCheck.processElectric(groupId);
//            }
//        }
    }

//    private static boolean hitokotoKeyword(String msg) {
//        for (String kw : KEYWORDS_HITOKOTO)
//            if (msg.contains(kw)) return true;
//        return false;
//    }
//
//    private static boolean likeUserKeyword(String msg) {
//        for (String kw : KEYWORDS_LIKE_USER)
//            if (msg.equalsIgnoreCase(kw)) return true;
//        return false;
//    }
//
//    private static boolean electricKeyword(String msg) {
//        for (String kw : KEYWORDS_ELECTRIC)
//            if (msg.equalsIgnoreCase(kw)) return true;
//        return false;
//    }
}