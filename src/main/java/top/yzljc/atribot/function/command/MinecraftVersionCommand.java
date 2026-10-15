package top.yzljc.atribot.function.command;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.command.SlashCommandArguments;
import top.yzljc.atribot.command.SlashCommandExecutor;
import top.yzljc.atribot.command.SlashCommandSender;
import top.yzljc.atribot.function.minecraft.McVersionImpl;
import top.yzljc.atribot.utils.FormatTools;

import java.util.Map;

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
