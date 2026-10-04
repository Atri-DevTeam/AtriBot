package top.yzljc.atribot.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
public class CommandFeature extends Command {
    @Getter
    private CommandExecutor executor;
    private SlashCommandExecutor slashExecutor;

    public SlashCommandExecutor getSlashExecutor() {
        if (slashExecutor != null) return slashExecutor;
        return getExecutor() instanceof SlashCommandExecutor slash ? slash : null;
    }

    SlashCommandExecutor getExplicitSlashExecutor() {
        return slashExecutor;
    }

    @Getter
    private List<CommandOptionDefinition> options;

    @Getter
    private final List<String> prefixlessAliases;

    public CommandFeature(String name, String description, String usage, List<String> aliases) {
        super(name, description, usage, aliases);
        this.options = List.of();
        this.prefixlessAliases = List.of();
    }

    public CommandFeature(String name, String description, String usage,
                          List<String> aliases, List<CommandOptionDefinition> options) {
        super(name, description, usage, aliases);
        this.options = options == null ? List.of() : List.copyOf(options);
        this.prefixlessAliases = List.of();
    }

    public CommandFeature(CommandDefinition definition) {
        super(definition.name(), definition.description(), definition.usage(), definition.aliases());
        this.options = definition.options();
        this.prefixlessAliases = definition.prefixlessAliases();
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (executor != null) {
            boolean success = executor.onCommand(sender, this, commandLabel, args);
            if (!success && !this.getUsage().isEmpty()) {
                sender.sendMessage("指令用法: " + this.getUsage());
            }
            return success;
        }
        return false;
    }
}
