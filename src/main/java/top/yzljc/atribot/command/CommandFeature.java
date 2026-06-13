package top.yzljc.atribot.command;

import lombok.Getter;
import lombok.Setter;
import top.yzljc.atribot.chat.official.TC;

import java.util.List;

@Setter
public class CommandFeature extends Command {
    @Getter
    private CommandExecutor executor;

    public CommandFeature(String name, String description, String usage, List<String> aliases, String featureKey,
                          boolean officialOnly, boolean onebotOnly) {
        super(name, description, usage, aliases, featureKey, officialOnly, onebotOnly);
    }

    public CommandFeature(CommandDefinition definition) {
        this(definition.name(), definition.description(), definition.usage(), definition.aliases(), definition.featureKey(),
                definition.officialOnly(), definition.onebotOnly());
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (commandLabel.equals("0") && isOfficialOnly()) {
            return true;
        }
        if (!commandLabel.equals("0") && isOnebotOnly()) {
            return true;
        }
        if (!checkEnable(sender) && commandLabel.equals("0")) {
            return true;
        }
        if (executor != null) {
            boolean success = executor.onCommand(sender, this, commandLabel, args);
            if (!success && !this.getUsage().isEmpty()) {
                if (commandLabel.equals("0")) {
                    sender.reply("用法: " + this.getUsage(), false);
                } else {
                    sender.replyMarkdown(commandLabel, TC.md("> 用法: " + this.getUsage()));
                }
            }
            return success;
        }
        return false;
    }
}
