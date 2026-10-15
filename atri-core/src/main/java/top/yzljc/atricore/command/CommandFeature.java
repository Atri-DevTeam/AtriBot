package top.yzljc.atricore.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 绑定文本执行器的指令，执行器返回 false 时发送配置的用法
 *
 * @Author YZ_Ljc_
 * @ClassName CommandFeature
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
@Setter
public class CommandFeature extends Command {
    @Getter
    private CommandExecutor executor;

    @Getter
    private final List<String> prefixlessAliases;

    public CommandFeature(String name, String description, String usage, List<String> aliases) {
        super(name, description, usage, aliases);
        this.prefixlessAliases = List.of();
    }

    public CommandFeature(CommandDefinition definition) {
        super(definition.name(), definition.description(), definition.usage(), definition.aliases());
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
