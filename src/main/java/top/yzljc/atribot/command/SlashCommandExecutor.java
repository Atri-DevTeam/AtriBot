package top.yzljc.atribot.command;

public interface SlashCommandExecutor extends CommandExecutor {
    boolean onSlashCommand(DiscordCommandSender sender, Command command, String label, SlashCommandArguments args);
}
