package top.yzljc.qqbot.utils.deprecated;

/**
 * @deprecated 旧版命令管理器，已废弃。请使用 {@link top.yzljc.qqbot.command.impl.CommandExecutor} 替代。
 */
@Deprecated(since = "2.6.1", forRemoval = true)
public interface ExecuteCommand {
    void execute(CommandContext ct);
}