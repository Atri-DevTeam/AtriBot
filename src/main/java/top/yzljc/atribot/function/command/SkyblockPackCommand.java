package top.yzljc.atribot.function.command;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.DiscordCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.command.SlashCommandArguments;
import top.yzljc.atribot.command.SlashCommandExecutor;
import top.yzljc.atribot.command.SlashCommandSender;

public final class SkyblockPackCommand implements CommandExecutor, SlashCommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof QQCommandSender qq) {
            return Atri.getInstance().getSkyblockPackCheck().onCommand(qq);
        }
        if (sender instanceof QQGuildCommandSender guild) {
            return Atri.getInstance().getSkyblockPackCheck().onCommand(guild);
        }
        return true;
    }

    @Override
    public boolean onCommand(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        return Atri.getInstance().getSkyblockPackCheck().onCommand(sender);
    }
}
