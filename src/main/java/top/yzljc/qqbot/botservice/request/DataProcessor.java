package top.yzljc.qqbot.botservice.request;

import top.yzljc.qqbot.botservice.tools.MM;
import top.yzljc.qqbot.botservice.tools.MT;
import top.yzljc.qqbot.botservice.userinfo.GetUserInfo;
import top.yzljc.qqbot.botservice.message.MessageFilter;
import top.yzljc.qqbot.botservice.message.MessageRecorder;
import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.botservice.userinfo.GetGroupInfo;
import top.yzljc.qqbot.command.impl.CommandManager;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.groups.GroupModeManager;
import top.yzljc.qqbot.debug.PacketEvent;
import top.yzljc.qqbot.feature.*;
import top.yzljc.qqbot.feature.minecraft.HypixelReward;
import top.yzljc.qqbot.utils.FindRecall;
import top.yzljc.qqbot.feature.minecraft.ServerRcon;
import top.yzljc.qqbot.utils.AutoAccept;
import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.utils.draft.Scratch;

import java.util.*;

public class DataProcessor {

    static Settings settings = Config.getInstance();
    private static final List<Long> spyGroups = settings.getMessageSpyGroups();
    private static final List<Long> admins = settings.getAdminUids();
    private static final long MANOSABA_GROUP = settings.getManosabaGroupId();
    private static final String[] KEYWORDS_HITOKOTO = settings.getKeywordsHitokoto();
    private static final String[] KEYWORDS_LIKE_USER = settings.getKeywordsLikeUser();
    private static final String[] KEYWORDS_ELECTRIC = {"电表", "dianbiao", "db"};
    private static final long ownerId = 3199590352L;

    public static void processMessage(JsonNode json) {
        String postType = json.path("post_type").asText("");
        String messageType = json.path("message_type").asText();
        String noticeType = json.path("notice_type").asText("");
        String subType = json.path("sub_type").asText("");
        String newName = json.path("name_new").asText("");
        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();
        String rawMessage = json.path("raw_message").asText();

        if ("message".equals(postType)) {
            JsonNode msgData = json.path("message");
            LinkedList<Map<String, Object>> messageContent = new LinkedList<>();

            for (JsonNode msgPart : msgData) {
                String msgType = msgPart.path("type").asText();
                Map<String, Object> msgList = new HashMap<>();
                Map<String, Object> rawMsgData = new HashMap<>();
                msgList.put("type", msgType);

                switch (msgType) {
                    case "text":
                        rawMsgData.put("text", msgPart.path("data").path("text").asText(""));
                        break;
                    case "face":
                        rawMsgData.put("id", msgPart.path("data").path("id").asText(""));
                        break;
                    case "json":
                        rawMsgData.put("data", msgPart.path("data").path("data").asText(""));
                        break;
                    case "image":
                        rawMsgData.put("file", msgPart.path("data").path("url").asText(""));
                        break;
                    case "video":
                        rawMsgData.put("file", msgPart.path("data").path("url").asText(""));
                        break;
                    case "at":
                        rawMsgData.put("qq", msgPart.path("data").path("qq").asText(""));
                        if (msgPart.path("data").path("qq").asText("").equals(String.valueOf(GetUserInfo.getBotId()))){
                            MT.atUser(ownerId,settings.getDebugGroupId(), GetUserInfo.getUserName(userId) + "在群" + GetGroupInfo.getGroupName(groupId) + "中提到了你");
                            MessageSender.sendGroupData(settings.getDebugGroupId(), MM.parse(rawMessage));
                        }
                        break;
                    case "reply":
                        rawMsgData.put("id", msgPart.path("data").path("id").asText(""));
                        break;
                    default:
                        break;
                }
                if (rawMsgData.isEmpty()) continue;
                msgList.put("data", rawMsgData);
                messageContent.add(msgList);
            }
            if ("private".equals(messageType)) {
                // 私聊转发到调试群并狠狠骚扰LJC
                List<Map<String, Object>> forward = new ArrayList<>();
                Map<String, Object> atAdmin = Map.of("type", "at","data", Map.of("qq", ownerId));
                Map<String, Object> notifyText = Map.of("type", "text","data", Map.of("text", "收到来自" + GetUserInfo.getUserName(userId) + "的私聊消息: "));
                forward.add(atAdmin);
                forward.add(notifyText);
                forward.addAll(messageContent);
                MessageSender.sendGroupData(settings.getDebugGroupId(), forward);
            }
            // 复读机消息拦截
            if (GroupConfigManager.isFeatureEnabled(groupId, "repeat_msg") && "group".equals(messageType)) {
                AutoRepeat.repeatGroupData(groupId, messageContent);
            }
        }

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


        if (groupId == 820103390L && subType.equals("group_name")){
            Scratch.shizoukiaGroupNameChange(userId, newName);
        }

        // 下面的内容都只处理消息类型
        if (!"message".equals(postType) && !"group".equals(messageType)) {
            return;
        }

        HypixelReward.processMessage(json);
        CommandManager.processCommand(json);
        GroupModeManager.process(json);
        AnnoyUser.processMessage(json);
        MessageRecorder.processRecord(json);
        ServerRcon.processMessage(json);

        // 不是我管的群我查个集贸，浪费资源
        if (spyGroups.contains(groupId) || userId == GetUserInfo.getBotId()) {
            MessageFilter.checkAndRecall(json);
        }

        if (GroupConfigManager.isFeatureEnabled(groupId,"bv_check")) {
            CheckBilibili.process(json);
        }

        if (groupId == MANOSABA_GROUP && userId == 3180644904L) {
            if (rawMessage.contains("[CQ:image")){
                Scratch.huffCount();
            }else{
                Scratch.stopHuff();
            }
        }

        if (hitokotoKeyword(rawMessage) && GroupConfigManager.isFeatureEnabled(groupId, "one_text") && userId != GetUserInfo.getBotId()){
            Hitokoto.processHitokoto(groupId);
        }

        if (likeUserKeyword(rawMessage) && GroupConfigManager.isFeatureEnabled(groupId,"like_user")){
            LikeUser.processCommand(userId, groupId);
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
