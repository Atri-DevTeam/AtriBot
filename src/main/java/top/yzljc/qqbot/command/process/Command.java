package top.yzljc.qqbot.command.process;

import top.yzljc.qqbot.config.groups.GroupConfigManager;

import java.util.List;

public abstract class Command {
    private final String uid;
    private final String description;
    private final String usageMessage;
    private final List<String> aliases;
    private final String enableFeature; // 对应原本的 featureKey

    protected Command(String uid, String description, String usageMessage, List<String> aliases, String enableFeature) {
        this.uid = uid;
        this.description = description;
        this.usageMessage = usageMessage;
        this.aliases = aliases;
        this.enableFeature = enableFeature;
    }

    public abstract boolean execute(CommandSender sender, String commandLabel, String[] args);

    public String getUid() {
        return uid;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usageMessage;
    }

    public List<String> getAliases() {
        return aliases;
    }

    // null = true
    public boolean checkEnable(CommandSender sender) {
        if (enableFeature == null || enableFeature.isEmpty()) {
            return true;
        }
        return GroupConfigManager.isFeatureEnabled(sender.getGroupId(), enableFeature);
    }

    public String getEnableFeature() {
        return enableFeature;
    }
}