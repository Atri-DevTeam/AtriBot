package top.yzljc.qqbot.config.groups;

import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;

import java.util.Map;

public class GroupConfigInfo implements CommandExecutor {
    private static final Map<String,Boolean> registeredFeatures = GroupConfigManager.getRegisteredFeatures();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        getGroupStatusDescription(sender.groupId());
        return true;
    }

    public static void getGroupStatusDescription(long groupId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 群 ").append(groupId).append(" 功能配置 ===\n");

        if (registeredFeatures.isEmpty()) {
            sb.append("（暂无注册功能）");
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
