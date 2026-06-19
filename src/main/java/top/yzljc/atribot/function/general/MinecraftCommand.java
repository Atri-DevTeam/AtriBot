package top.yzljc.atribot.function.general;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.function.official.minecraft.DiceImpl;
import top.yzljc.atribot.function.official.minecraft.VersionCheckImpl;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftCommand
 * @Created_at 2026/05/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
public class MinecraftCommand implements CommandExecutor {

    private static final Markdown ValidCommands = TC.md(
            Markdown.img("https://www.yzljc.top/img/grass-block-img.png", 24, 24) + " **Minecraft 指令列表**\n\n" +
                    "1. " + Markdown.enterCommand("/mc dice", "/mc dice") + " - Skyblock High Class Archfiend Dice\n\n" +
                    "2. " + Markdown.enterCommand("/mc ver", "/mc ver") + " - 查看当前最新的MC版本"
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) return true;

        if (args.length < 1) {
            sender.sendMessage(ValidCommands);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equalsIgnoreCase("dice")) {
            DiceImpl.handle(sender, args);
            return true;
        }

        if (subCommand.equals("version") || subCommand.equals("ver")) {
            VersionCheckImpl.onCommand(sender);
            return true;
        }

        sender.sendMessage(ValidCommands);
        return true;
    }
}
