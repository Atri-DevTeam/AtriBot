package top.yzljc.atribot.command;

import lombok.Getter;
import lombok.Setter;
import top.yzljc.atribot.config.groups.GroupConfigManager;

import java.util.List;

public abstract class Command {
    @Getter
    private final String name;
    /**
     * 用户实际输入的命令标签（可能是主命令名或别名），在 {@code CommandMap#dispatch} 中设置。
     * 仅在命令执行期间有效，执行完成后不保证保留原值。
     */
    @Getter
    @Setter
    private volatile String invokedLabel;
    @Getter
    private final String description;
    private final String usageMessage;
    @Getter
    private final List<String> aliases;
    @Getter
    private final String enableFeature;
    @Getter
    private final boolean officialOnly;
    @Getter
    private final boolean onebotOnly;

    protected Command(String name, String description, String usageMessage, List<String> aliases, String enableFeature,
                      boolean officialOnly, boolean onebotOnly) {
        this.name = name;
        this.description = description;
        this.usageMessage = usageMessage;
        this.aliases = aliases;
        this.enableFeature = enableFeature;
        this.officialOnly = officialOnly;
        this.onebotOnly = onebotOnly;
    }

    public abstract boolean execute(CommandSender sender, String commandLabel, String[] args);

    public String getUsage() {
        return usageMessage;
    }

    @Deprecated
    public String getUid() {
        return name;
    }

    public String getFeatureKey() {
        return enableFeature;
    }

    // null = true
    public boolean checkEnable(CommandSender sender) {
        if (enableFeature == null || enableFeature.isEmpty()) {
            return true;
        }
        return GroupConfigManager.isFeatureEnabled(sender.groupId(), enableFeature);
    }
}
