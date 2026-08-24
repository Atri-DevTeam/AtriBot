package top.yzljc.atribot.platform.napcat.groupfunction;

import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.ConsoleCommandSender;
import top.yzljc.atribot.command.DiscordCommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.command.SlashCommandArguments;
import top.yzljc.atribot.command.SlashCommandExecutor;

import java.util.Map;

public class GroupConfigInfo implements CommandExecutor, SlashCommandExecutor {
    private static final Map<String, Boolean> registeredFeatures = GroupConfigManager.getRegisteredFeatures();

    @Override
    public boolean onSlashCommand(DiscordCommandSender sender, Command command, String label, SlashCommandArguments args) {
        return onCommand(sender, command, label, args == null ? new String[0] : args.toArray());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof NapcatCommandSender nc) {
            getGroupStatusDescription(nc.getGroupId());
            return true;
        }
        if (sender instanceof ConsoleCommandSender) {
            if (args.length < 1) {
                sender.sendMessage("用法: /groupinfo <群号>");
                return true;
            }
            sender.sendMessage(buildDescription(args[0]));
            return true;
        }
        return true;
    }

    public static void getGroupStatusDescription(String groupId) {
        GroupMessage.chatMessage(groupId, buildDescription(groupId));
    }

    public static String buildDescription(String groupId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 群 ").append(groupId).append(" 功能配置 ===\n");

        if (registeredFeatures.isEmpty()) {
            sb.append("（暂无注册功能）");
            return sb.toString().trim();
        }

        for (String featureName : registeredFeatures.keySet()) {
            boolean isEnabled = GroupConfigManager.isFeatureEnabled(groupId, featureName);
            sb.append(isEnabled ? "✅ [开启] " : "❌ [关闭] ")
                    .append(featureName)
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
