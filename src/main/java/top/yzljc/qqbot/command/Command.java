package top.yzljc.qqbot.command;

import lombok.Getter;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

import java.util.List;

public abstract class Command {
    @Getter
    private final String uid;
    @Getter
    private final String description;
    private final String usageMessage;
    @Getter
    private final List<String> aliases;
    @Getter
    private final String enableFeature; // 对应原本的 featureKey

    protected Command(String uid, String description, String usageMessage, List<String> aliases, String enableFeature) {
        this.uid = uid;
        this.description = description;
        this.usageMessage = usageMessage;
        this.aliases = aliases;
        this.enableFeature = enableFeature;
    }

    public abstract boolean execute(CommandSender sender, String commandLabel, String[] args);

    public String getUsage() {
        return usageMessage;
    }

    // null = true
    public boolean checkEnable(CommandSender sender) {
        if (enableFeature == null || enableFeature.isEmpty()) {
            return true;
        }
        return GroupConfigManager.isFeatureEnabled(sender.groupId(), enableFeature);
    }

}