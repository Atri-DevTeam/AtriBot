package top.yzljc.qqbot.command.impl;

public interface CommandExecutor {
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}