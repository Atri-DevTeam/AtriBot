package top.yzljc.qqbot.config;

import top.yzljc.qqbot.botkits.userinfo.GetFriendList;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

public class Reload implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限这样做！", false);
            return true;
        }
        if (args.length == 0) {
            Config.getInstance().reload();
            GroupConfigManager.refreshAllConfigs();
            GetFriendList.updateFriendList();
            return true;
        }
        if (args.length > 1) {
            if (args[0].equalsIgnoreCase("g")) {
                GroupConfigManager.refreshAllConfigs();
            } else if (args[0].equalsIgnoreCase("f")) {
                GetFriendList.updateFriendList();
            } else if (args[0].equalsIgnoreCase("cfg")) {
                Config.getInstance().reload();
            } else {
                Config.getInstance().reload();
                GroupConfigManager.refreshAllConfigs();
                GetFriendList.updateFriendList();
            }
        }
        return true;
    }
}
