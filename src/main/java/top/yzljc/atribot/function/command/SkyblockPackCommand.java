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
    public boolean onSlashCommand(DiscordCommandSender sender, Command command, String label,
                                  SlashCommandArguments args) {
        return Atri.getInstance().getSkyblockPackCheck().onCommand(sender);
    }
}
