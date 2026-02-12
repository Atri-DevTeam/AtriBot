package top.yzljc.qqbot.config.groups;

import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.command.CommandContext;
import top.yzljc.qqbot.command.ExecuteCommand;

import java.util.Map;

public class GroupConfigInfo implements ExecuteCommand {
    private static final Map<String,Boolean> registeredFeatures = GroupConfigManager.getRegisteredFeatures();

    @Override
    public void execute(CommandContext ct) {
        getGroupStatusDescription(ct.getGroupId());
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
        MessageSender.sendGroupMessage(groupId,sb.toString().trim());
    }
}
