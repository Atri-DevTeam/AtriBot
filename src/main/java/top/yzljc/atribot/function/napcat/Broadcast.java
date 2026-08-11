package top.yzljc.atribot.function.napcat;

import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.FormatTools;

import java.util.Set;

public class Broadcast implements CommandExecutor {
    private static final Set<String> groupList = GroupInformation.fetchAllGroupIds();
    private static final String debugGroupId = Config.getInstance().getNapcatDebugGroupUin();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "broadcast")) return true;
        if (!nc.hasPermission()) {
            nc.sendMessage("你没有权限执行此命令");
            return true;
        }
        if (args.length < 1) {
            return false;
        }

        fetchToGroups(String.join(" ", args));
        return true;
    }

    private static void fetchToGroups(String message) {
        for (String gid : groupList) {
            if (!GroupConfigManager.isFeatureEnabled(gid, "broadcast")) continue;
            ThreadManager.execute(() -> GroupMessage.chatMessage(gid, FormatTools.unescape(message)));
        }
    }

    @Deprecated
    private static void debugBroadcastRequest(String message) {
        GroupMessage.chatMessage(debugGroupId, FormatTools.unescape(message));
    }
}
