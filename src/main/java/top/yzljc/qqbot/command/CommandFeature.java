package top.yzljc.qqbot.command;

import lombok.Setter;
import top.yzljc.qqbot.AtriBot;

import java.util.List;

@Setter
public class CommandFeature extends Command {
    private CommandExecutor executor;

    public CommandFeature(String name, String description, String usage, List<String> aliases, String featureKey) {
        super(name, description, usage, aliases, featureKey);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!checkEnable(sender) && commandLabel.equals("0")) {
            return true;
        }
        if (executor != null) {
            boolean success = executor.onCommand(sender, this, commandLabel, args);
            if (!success && !this.getUsage().isEmpty()) {
                switch (commandLabel) {
                    case "0" -> sender.reply("用法: " + this.getUsage(), false);
                    case "1" ->
                            AtriBot.getInstance().getMessageService().replyPrivateMarkdownMessage(sender.userOpenId(), sender.messageOpenId(), "> 用法: " + this.getUsage());
                    case "2" ->
                            AtriBot.getInstance().getMessageService().replyGroupMarkdownMessage(sender.groupOpenId(), sender.messageOpenId(), "> 用法: " + this.getUsage());
                }
            }
            return success;
        }
        return false;
    }
}