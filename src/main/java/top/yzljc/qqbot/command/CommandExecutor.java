package top.yzljc.qqbot.command;

public interface CommandExecutor {
    // 仅对原有实现进行兼容
    default boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return onCommand(sender, command, 0, args);
    }

    default boolean onCommand(CommandSender sender, Command command, int label, String[] args) {
        return true;
    }
}