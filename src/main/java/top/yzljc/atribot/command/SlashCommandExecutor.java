package top.yzljc.atribot.command;

public interface SlashCommandExecutor extends CommandExecutor {
    boolean onSlashCommand(DiscordSlashCommandSender sender, Command command, String label, SlashCommandArguments args);
}
