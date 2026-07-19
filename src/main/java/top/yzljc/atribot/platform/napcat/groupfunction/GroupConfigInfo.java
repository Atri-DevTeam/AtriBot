package top.yzljc.atribot.platform.napcat.groupfunction;

import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;

import java.util.Map;

public class GroupConfigInfo implements CommandExecutor {
    private static final Map<String, Boolean> registeredFeatures = GroupConfigManager.getRegisteredFeatures();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        getGroupStatusDescription(sender.getGroupId());
        return true;
    }

    public static void getGroupStatusDescription(String groupId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 群 ").append(groupId).append(" 功能配置 ===\n");

        if (registeredFeatures.isEmpty()) {
            sb.append("（暂无注册功能）");
            GroupMessage.chatMessage(groupId, sb.toString().trim());
            return;
        }

        for (String featureName : registeredFeatures.keySet()) {
            boolean isEnabled = GroupConfigManager.isFeatureEnabled(groupId, featureName);
            sb.append(isEnabled ? "✅ [开启] " : "❌ [关闭] ")
                    .append(featureName)
                    .append("\n");
        }
        GroupMessage.chatMessage(groupId, sb.toString().trim());
    }
}
