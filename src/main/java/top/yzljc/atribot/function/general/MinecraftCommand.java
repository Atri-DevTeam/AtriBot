package top.yzljc.atribot.function.general;

import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.function.official.BanTracker;
import top.yzljc.atribot.function.official.minecraft.DiceImpl;
import top.yzljc.atribot.function.official.minecraft.MinecraftCapes;
import top.yzljc.atribot.function.official.minecraft.MinecraftVersionCheck;
import top.yzljc.atribot.function.official.minecraft.PackMcmetaGenerator;
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
            Markdown.img(ResourcesProperties.GRASS_BLOCK_IMG, 24, 24) + " **Minecraft 指令列表**\n\n" +
                    "1. " + Markdown.enterCommand("/mc dice", "/mc dice") + " - Skyblock High Class Archfiend Dice\n\n" +
                    "2. " + Markdown.enterCommand("/mc ver", "/mc ver") + " - 查看当前最新的MC版本\n\n" +
                    "3. " + Markdown.enterCommand("/bantracker", "/bantracker") + " - Hypixel BanTracker查询\n\n" +
                    "4. " + Markdown.enterCommand("/mc capes", "/mc capes") + " - Minecraft全部Capes使用情况\n\n" +
                    "5. " + Markdown.enterCommand("/mc pack ", "/mc pack [版本]") + " - 生成MC资源包版本信息"
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) return true;


        if (label.equals("bt") || label.equals("bantracker")) {
            return BanTracker.handle(sender, command, label, args);
        }

        if (args.length < 1) {
            sender.sendMessage(ValidCommands);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equalsIgnoreCase("dice")) {
            DiceImpl.handle(sender, args);
            return true;
        }

        switch (subCommand) {
            case "version", "ver" -> {
                MinecraftVersionCheck.onCommand(sender);
                return true;
            }
            case "capes", "cape" -> {
                return MinecraftCapes.handleCapesCommand(sender);
            }
            case "pack" -> {
                PackMcmetaGenerator.handle(sender, args);
                return true;
            }
        }

        sender.sendMessage(ValidCommands);
        return true;
    }
}
