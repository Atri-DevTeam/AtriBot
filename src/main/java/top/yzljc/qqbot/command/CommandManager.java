package top.yzljc.qqbot.command;

import top.yzljc.qqbot.service.userinfo.GetUserInfo;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;

import java.util.Collections;
import java.util.List;

public class CommandManager implements Listener {
    private static final Settings settings = Config.getInstance();
    private static final String COMMAND_PREFIX = settings.getCommandPrefix();
    private static final long BOT_QQ = GetUserInfo.getBotId();
    private static final String DEBUG_SUFFIX = settings.getDebugCommandSuffix();
    private static final List<Long> adminUids = settings.getAdminUids();

    private static final CommandMap commandMap = new CommandMap();

    public static CommandFeature getCommand(String name) {
        return (CommandFeature) commandMap.getCommand(name);
    }

    public static void registerCommand(String name, String description, String usage, List<String> aliases, String featureKey) {
        if (usage == null) usage = "/" + name;
        if (aliases == null) aliases = Collections.emptyList();

        CommandFeature command = new CommandFeature(name, description, usage, aliases, featureKey);
        commandMap.register("atri-core", command);
    }

    public static void registerCommand(String name, CommandExecutor executor, String description, String usage, List<String> aliases, String featureKey) {
        if (usage == null) {
            usage = "/" + name;
        }
        if (aliases == null) {
            aliases = Collections.emptyList();
        }

        CommandFeature command = new CommandFeature(name, description, usage, aliases, featureKey);
        command.setExecutor(executor);

        commandMap.register("atri-core", command);
    }

    static {
        registerCommand("happynewyear", "发送新年快乐", null, null, "new_year");
        registerCommand("bc", "全服广播", "/bc <内容>", Collections.singletonList("broadcast"), "broadcast");
        registerCommand("wakeup", "唤醒Bot", null, null, "wakeup_send");
        registerCommand("reboot", "重启Bot", null, null, null);
        registerCommand("search", "搜索聊天记录", "/search \"关键词\" [-u QQ] [-m p/a]", null, null);
        registerCommand("recall", "撤回上一条消息", null, null, null);
        registerCommand("rollback", "批量撤回消息", "/rollback [-n 数量] [-u QQ号]", null, null);
        registerCommand("motd", "查询MC服务器状态", "/motd <服务器ip地址(端口号可选)>", null, "motd");
        registerCommand("mojang", "查询Mojang服务状态", null, null, "mojang_status");
        registerCommand("cl", "领取Hypixel奖励", "/cl <Hypixel每日签到链接>", null, "get_hypixel_reward");
        registerCommand("bwc", "起床战争挑战查询", "/bwc <玩家名> 或 /bwc api <API Key>", null, "bedwars_challenge");
        registerCommand("checkmcnews", "获取MC新闻", null, null, "mc_news");
        registerCommand("checkhypnews", "获取Hypixel新闻", null, null, "hyp_news");
        registerCommand("manodate", "Manosaba日期查询", null, null, null);
        registerCommand("github", "/github [群号1] [群号2]...", null, null, "github_info");
        registerCommand("signall", "自动签到", null, null, "auto_sign");
        registerCommand("stats", "消息统计", "/stats 查询当日发言排行信息, /stats [y | overall | @user] 查询昨日/总计/指定用户的发言统计信息", null, null);
        registerCommand("groupinfo", "查看群组配置", null, null, null);
        registerCommand("calendar", "查看日历", null, null, "calendar");
        registerCommand("atrihelp", "显示帮助菜单", "/atrihelp", null, null);
        registerCommand("emj", "表情轰炸", "/emj <normal/medium/insane/animation> [可选: User]", null, "annoy_user");
        registerCommand("reload", "重新加载配置", "/reload [all|cfg|f|g]", null, null);
        registerCommand("autolike", "自动点赞列表", "/autolike add|remove|list [可选: User]", null, null);
        registerCommand("tufe", "查课", "/tufe查看下节课的上课地点", null, "tufe_class_alert");
        registerCommand("verify", "验证YZ_Ljc_ Network账号", "/verify <验证密钥>", null, "verify_server");
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

    @EventHandler
    public void processCommand(GroupMessageEvent event) {
        long userId = event.getUserId();
        if (userId == BOT_QQ) return;

        String rawMessage = event.getRawMessage().trim();

        if (!rawMessage.startsWith(COMMAND_PREFIX)) {
            return;
        }

        long groupId = event.getGroupId();
        boolean isAdmin = adminUids.contains(userId);
        boolean isDebug = false;
        long messageId = event.getMessageId();

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
}