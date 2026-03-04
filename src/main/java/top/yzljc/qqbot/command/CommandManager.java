package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botservice.userinfo.GetUserInfo;
import top.yzljc.qqbot.command.impl.Reboot;
import top.yzljc.qqbot.command.impl.RollbackMessages;
import top.yzljc.qqbot.command.impl.SearchRelevant;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Reload;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigInfo;
import top.yzljc.qqbot.debug.Broadcast;
import top.yzljc.qqbot.botservice.tools.RM;
import top.yzljc.qqbot.feature.AnnoyUser;
import top.yzljc.qqbot.feature.HappyNewYear;
import top.yzljc.qqbot.feature.github.WebhookServer;
import top.yzljc.qqbot.feature.minecraft.BedwarsChallenge;
import top.yzljc.qqbot.feature.minecraft.HypixelReward;
import top.yzljc.qqbot.feature.minecraft.MojangStatus;
import top.yzljc.qqbot.feature.minecraft.Motd;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.feature.schedule.*;
import top.yzljc.qqbot.utils.AtriHelp;
import top.yzljc.qqbot.utils.draft.AutoLikeCommand;

import java.util.Collections;
import java.util.List;

public class CommandManager {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);
    private static final Settings settings = Config.getInstance();
    private static final String COMMAND_PREFIX = settings.getCommandPrefix();
    private static final long BOT_QQ = GetUserInfo.getBotId();
    private static final String DEBUG_SUFFIX = settings.getDebugCommandSuffix();
    private static final List<Long> adminUids = settings.getAdminUids();

    private static final CommandMap commandMap = new CommandMap();

    static {
        register("happynewyear", new HappyNewYear(), "发送新年快乐", null, null, "new_year");
        register("bc", new Broadcast(), "全服广播", "/bc <内容>", Collections.singletonList("broadcast"), "broadcast");
        register("wakeup", new WakeUp(), "唤醒Bot", null, null, "wakeup_send");
        register("reboot", new Reboot(), "重启Bot", null, null, null);
        register("search", new SearchRelevant(), "搜索聊天记录", "/search \"关键词\" [-u QQ] [-m p/a]", null, null);
        register("recall", new RM(), "撤回上一条消息", null, null, null);
        register("rollback", new RollbackMessages(), "批量撤回消息", "/rollback [-n 数量] [-u QQ号]", null, null);
        register("motd", new Motd(), "查询MC服务器状态", "/motd <服务器ip地址(端口号可选)>", null, "motd");
        register("mojang", new MojangStatus(), "查询Mojang服务状态", null, null, "mojang_status");
        register("cl", new HypixelReward(), "领取Hypixel奖励", "/cl <Hypixel每日签到链接>", null, "get_hypixel_reward");
        register("bwc", new BedwarsChallenge(), "起床战争挑战查询", "/bwc <玩家名> 或 /bwc api <API Key>", null, "bedwars_challenge");
        register("checkmcnews", new MinecraftNews(), "获取MC新闻", null, null, "mc_news");
        register("checkhypnews", new HypixelNews(), "获取Hypixel新闻", null, null, "hyp_news");
        register("manodate", new ManosabaDate(), "Manosaba日期查询", null, null, null);
        register("github", new WebhookServer(), "/github [群号1] [群号2]...", null, null, "github_info");
        register("signall", new AutoSign(), "自动签到", null, null, "auto_sign");
        register("stats", new MessageStats(), "消息统计", "/stats 查询当日发言排行信息, /stats [y | overall | @user] 查询昨日/总计/指定用户的发言统计信息", null, null);
        register("groupinfo", new GroupConfigInfo(), "查看群组配置", null, null, null);
        register("calendar", new Calendar(), "查看日历", null, null, "calendar");
        register("atrihelp", new AtriHelp(), "显示帮助菜单", "/atrihelp", null, null);
        register("emj", new AnnoyUser(), "表情轰炸", "/emj <normal/medium/insane/animation> [可选: User]", null, "annoy_user");
        register("reload", new Reload(), "重新加载配置", "/reload [all|cfg|f|g]", null, null);
        register("autolike", new AutoLikeCommand(), "自动点赞列表", "/autolike add|remove|list [可选: User]", null, null);
        register("tufe", new TufeClassAlert(), "查课", "/tufe查看下节课的上课地点", null, null);
    }

    /**
     * 注册命令的辅助方法
     * @param name 命令名称 (例如 "motd")
     * @param executor 命令执行逻辑类
     * @param description 命令描述
     * @param usage 用法提示 (传 null 则默认为 /<name>)
     * @param aliases 别名列表 (传 null 则无别名)
     * @param featureKey 对应 config.yml 中的功能开关 key (传 null 则默认开启)
     */
    private static void register(String name, CommandExecutor executor, String description, String usage, List<String> aliases, String featureKey) {
        if (usage == null) {
            usage = "/" + name;
        }
        if (aliases == null) {
            aliases = Collections.emptyList();
        }

        CommandFeature command = new CommandFeature(name, description, usage, aliases, featureKey);
        command.setExecutor(executor);

        commandMap.register("atri-core",command);
    }

    public static void processCommand(JsonNode json) {
        String postType = json.path("post_type").asText("");
        String messageType = json.path("message_type").asText("");

        if (!"message".equals(postType) || !"group".equals(messageType)) {
            return;
        }

        long userId = json.path("user_id").asLong();
        if (userId == BOT_QQ) return;

        String rawMessage = json.path("raw_message").asText("").trim();

        if (!rawMessage.startsWith(COMMAND_PREFIX)) {
            return;
        }

        long groupId = json.path("group_id").asLong();
        boolean isAdmin = adminUids.contains(userId);
        boolean isDebug = false;
        int messageId = json.path("message_id").asInt(0);

        String commandContent = rawMessage.substring(COMMAND_PREFIX.length());

        if (isAdmin && rawMessage.endsWith(DEBUG_SUFFIX)) {
            isDebug = true;
            commandContent = commandContent.substring(0, commandContent.length() - DEBUG_SUFFIX.length()).trim();
        }

        CommandSender sender = new CommandSender(userId, groupId, isAdmin, isDebug, messageId);

        boolean executed = commandMap.dispatch(sender, commandContent);

        if (!executed) {
            // 命令未找到或执行失败，发送错误提示
        }
    }
    
    // 提供给外部获取所有注册命令的方法，用于生成帮助菜单
    public static CommandMap getCommandMap() {
        return commandMap;
    }
}