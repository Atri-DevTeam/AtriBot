package top.yzljc.qqbot.official.function;

import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftUtils
 * @Created_at 2026/05/11
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
public class MinecraftUtils implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equals("0")) {
            return true;
        }
        if (args.length < 1) {
            return false;
        }
        if (args[0].equalsIgnoreCase("version")) {
            sender.replyMarkdown(label, "> 当前社区Minecraft版本为 1.7.x - 26.1.x");
        }
        return true;
    }
}