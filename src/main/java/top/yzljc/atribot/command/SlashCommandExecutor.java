package top.yzljc.atribot.command;

/**
 * @Author YZ_Ljc_
 * @ClassName SlashCommandExecutor
 * @Created_at 2026/07/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
@FunctionalInterface
public interface SlashCommandExecutor {
    boolean onCommand(SlashCommandSender sender, Command command, String label, SlashCommandArguments args);
}
