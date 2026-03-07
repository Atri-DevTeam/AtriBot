package top.yzljc.qqbot.debug;

import top.yzljc.qqbot.botservice.userinfo.GetGroupInfo;
import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.botservice.thread.ThreadManager;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botservice.tools.FT;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

import java.util.Set;

public class Broadcast implements CommandExecutor {
    private static final Set<Long> GroupList = GetGroupInfo.fetchAllGroupIds();
    static Settings settings = Config.getInstance();
    private static final long DebugGroupId = settings.getDebugGroupId();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        if (args.length < 1) {
            return false;
        }

        fecthToGroups(String.join(" ", args));
        return true;
    }

    private static void fecthToGroups(String message) {
        for (long gid : GroupList) {
            if (!GroupConfigManager.isFeatureEnabled(gid, "broadcast")) continue;
            ThreadManager.execute(() -> MessageSender.sendGroupMessage(gid, FT.unescape(message)));
        }
    }

    private static void debugBroadcastRequest(String message) {
        MessageSender.sendGroupMessage(DebugGroupId, FT.unescape(message));
    }
}
