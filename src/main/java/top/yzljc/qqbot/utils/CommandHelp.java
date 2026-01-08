package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 指令帮助菜单
 * 监听 [CQ:at,qq=970717559] /help 并回复
 */
public class CommandHelp {

    static Settings settings = Config.getInstance();
    private static final long BOT_QQ = settings.getBotUid();
    private static final String HELP_TRIGGER = "/help";

    private static final Map<String, String> commonCommands = new LinkedHashMap<>();
    private static final Map<String, String> adminCommands = new LinkedHashMap<>();

    static {
        // ==== 在这里添加普通用户指令 ====
        commonCommands.put("/stats", "查看今日发言排行（仅开启有效）");
        commonCommands.put("/statsoverall", "查看历史发言总计（仅开启有效）");
        commonCommands.put("/bl BVxxxx", "解析B站视频信息（输入BV号）");
        commonCommands.put("/mojang", "查询 Mojang 服务器状态");
        commonCommands.put("赞我", "名片点赞 10 次");
        commonCommands.put("一言", "随机一言");
        commonCommands.put("/emj","贴表情刷屏");
        commonCommands.put("/ayme","每条消息获得三个表情");
        commonCommands.put("/ayrme","每条消息不再获取贴表情");
        commonCommands.put("db", "查询宿舍电表信息（仅限特定群聊）");
        commonCommands.put("/search \"text\" [-m a/p]","查询历史消息[模糊/精准]（仅开启有效）");
        commonCommands.put("/happynewyear","查询新年倒计时");

        // ==== 在这里添加管理员指令 ====
        adminCommands.put("/manodate", "查看某项目开发进度");
        adminCommands.put("/rc <Server> <Cmd>", "发送服务器控制台指令");
        adminCommands.put("/ay(ayr) @user -s", "开启/关闭指定用户的超级贴表情");
        adminCommands.put("/emj @user","为指定用户开启贴表情刷屏");
        adminCommands.put("/debug [群号]", "开启/关闭数据包抓取");
        adminCommands.put("/reboot", "重启机器人程序");
        adminCommands.put("/testformc", "手动检查 MC 新闻");
        adminCommands.put("/testforhyp", "手动检查 Hypixel 新闻");
        adminCommands.put("/testforsign","群打卡测试");
        adminCommands.put("/rollback [-n 数量] [-u QQ号]", "撤回数据库中记录的消息");
    }

    public static void process(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return;
        if (!"group".equals(json.path("message_type").asText())) return;

        String rawMsg = json.path("raw_message").asText().trim();
        long groupId = json.path("group_id").asLong();

        // 匹配逻辑：被艾特 且 包含 /help
        if (rawMsg.contains("[CQ:at,qq=" + BOT_QQ + "]") && rawMsg.toLowerCase().contains(HELP_TRIGGER)) {
            sendHelpMenu(groupId);
        }
    }

    private static void sendHelpMenu(long groupId) {
        StringBuilder sb = new StringBuilder();
        sb.append("✨ YZ_Ljc_ Bot 指令菜单 ✨\n");
        sb.append("----------------------------\n");

        sb.append("💡 [普通指令]\n");
        commonCommands.forEach((cmd, desc) -> {
            sb.append("▶ ").append(cmd).append(" : ").append(desc).append("\n");
        });

        sb.append("\n👑 [管理指令]\n");
        adminCommands.forEach((cmd, desc) -> {
            sb.append("⭐ ").append(cmd).append(" : ").append(desc).append("\n");
        });

        sb.append("----------------------------\n");
        sb.append("💡 使用配置菜单请发送 /cfg\n");
        sb.append("💡 强制保存配置请发送 /save");

        MessageSender.sendGroupMessage(groupId, sb.toString());
    }

    /**
     * 【扩展接口】如果其他类想动态添加指令说明，可以调用此方法
     */
    public static void registerCommand(String cmd, String desc, boolean isAdmin) {
        if (isAdmin) {
            adminCommands.put(cmd, desc);
        } else {
            commonCommands.put(cmd, desc);
        }
    }
}