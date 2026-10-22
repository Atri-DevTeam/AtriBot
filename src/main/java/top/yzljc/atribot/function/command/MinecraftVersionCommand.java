package top.yzljc.atribot.function.command;

import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.command.QQGuildCommandSender;
import top.yzljc.atribot.function.minecraft.McVersionImpl;

public final class MinecraftVersionCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof QQCommandSender qq) {
            return McVersionImpl.onCommand(qq);
        }
        if (sender instanceof QQGuildCommandSender guild) {
            return McVersionImpl.onCommand(guild);
        }
        return true;
    }

}
