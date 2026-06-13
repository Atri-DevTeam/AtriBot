package top.yzljc.atribot.feature;

import top.yzljc.atribot.chat.onebot.GroupInformation;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.utils.tools.FT;
import top.yzljc.atribot.config.groups.GroupConfigManager;

import java.util.Set;

public class Broadcast implements CommandExecutor {
    private static final Set<Long> GroupList = GroupInformation.fetchAllGroupIds();
    private static final long DebugGroupId = Config.getInstance().getDebugGroupId();

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
            ThreadManager.execute(() -> GroupMessage.chatMessage(gid, FT.unescape(message)));
        }
    }

    @Deprecated
    private static void debugBroadcastRequest(String message) {
        GroupMessage.chatMessage(DebugGroupId, FT.unescape(message));
    }
}
