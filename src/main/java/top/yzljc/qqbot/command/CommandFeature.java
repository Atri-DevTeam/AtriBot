package top.yzljc.qqbot.command;

import lombok.Setter;

import java.util.List;

@Setter
public class CommandFeature extends Command {
    private CommandExecutor executor;

    public CommandFeature(String name, String description, String usage, List<String> aliases, String featureKey) {
        super(name, description, usage, aliases, featureKey);
    }

    @Override
    public boolean execute(CommandSender sender, int commandLabel, String[] args) {
        if (!checkEnable(sender)) {
            return true;
        }
        if (executor != null) {
            boolean success = executor.onCommand(sender, this, commandLabel, args);
            if (!success && !this.getUsage().isEmpty()) {
                sender.reply("用法: " + this.getUsage(),false);
            }
            return success;
        }
        return false;
    }
}