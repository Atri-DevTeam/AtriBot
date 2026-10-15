package top.yzljc.atribot.function.command;

import top.yzljc.atribot.Atri;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.command.QQGuildCommandSender;

public final class SkyblockPackCommand implements CommandExecutor {

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
}
