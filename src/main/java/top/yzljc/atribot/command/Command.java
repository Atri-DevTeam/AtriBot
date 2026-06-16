package top.yzljc.atribot.command;

import lombok.Getter;

import java.util.List;

public abstract class Command {
    @Getter
    private final String name;
    @Getter
    private final String description;
    @Getter
    private final String usageMessage;
    @Getter
    private final List<String> aliases;

    protected Command(String name, String description, String usageMessage, List<String> aliases) {
        this.name = name;
        this.description = description;
        this.usageMessage = usageMessage;
        this.aliases = aliases;
    }

    public abstract boolean execute(CommandSender sender, String commandLabel, String[] args);

    public String getUsage() {
        return usageMessage;
    }

    @Deprecated
    public String getUid() {
        return name;
    }
}
