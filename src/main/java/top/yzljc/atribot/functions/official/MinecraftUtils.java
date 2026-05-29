package top.yzljc.atribot.functions.official;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.functions.official.minecraft.DiceImpl;
import top.yzljc.atribot.functions.official.minecraft.NewsImpl;
import top.yzljc.atribot.functions.official.minecraft.VersionCheckImpl;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftUtils
 * @Created_at 2026/05/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
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

        String subCommand = args[0].toLowerCase();

        if (subCommand.equalsIgnoreCase("dice")) {
            DiceImpl.handle(sender, label, args);
            return true;
        }

        if (subCommand.equals("news")) {
            NewsImpl.handle(sender, label, args);
            return true;
        }

        if (subCommand.equals("version") || subCommand.equals("ver")) {
            if (sender.isDebug() && sender.isAdmin()) {
                VersionCheckImpl.checkVersion();
                return true;
            }
            VersionCheckImpl.onCommand(sender, label);
            return true;
        }

        return false;
    }
}
