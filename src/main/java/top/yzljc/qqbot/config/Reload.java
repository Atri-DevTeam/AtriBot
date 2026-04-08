package top.yzljc.qqbot.config;

import top.yzljc.qqbot.service.userinfo.GetFriendList;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

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
                GetFriendList.updateFriendList();
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
        GroupConfigManager.refreshAllConfigs();
        GetFriendList.updateFriendList();
    }
}
