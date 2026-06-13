package top.yzljc.atribot.config;

import top.yzljc.atribot.chat.onebot.FriendList;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.groups.GroupConfigManager;

public class Reload implements CommandExecutor {

    private static final String GROUP = "g";
    private static final String FRIEND = "f";
    private static final String CONFIG = "cfg";
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限这样做！", false);
            return true;
        }

        if (args.length == 0) {
            reloadAll();
            sender.reply("所有配置已重新加载", false);
            return true;
        }

        String arg = args[0].toLowerCase();
        switch (arg) {
            case GROUP:
                GroupConfigManager.refreshAllConfigs();
                sender.reply("群组配置已刷新", false);
                break;
            case FRIEND:
                FriendList.updateFriendList();
                sender.reply("好友列表已更新", false);
                break;
            case CONFIG:
                Config.getInstance().reload();
                sender.reply("全局配置已重新加载", false);
                break;
            default:
                return false;
        }

        return true;
    }

    private void reloadAll() {
        Config.getInstance().reload();
        CommandManager.reload();
        GroupConfigManager.refreshAllConfigs();
        FriendList.updateFriendList();
    }
}
