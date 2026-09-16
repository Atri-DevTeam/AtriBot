package top.yzljc.atribot.plugin;

import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandFeature;
import top.yzljc.atribot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName PluginCommand
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 */
public final class PluginCommand extends CommandFeature {
    private final AtriPlugin plugin;
    private final String qualifiedName;
    private final String permission;
    private final String permissionMessage;
    private volatile CommandExecutor executor;
    private volatile boolean active;

    PluginCommand(AtriPlugin plugin, PluginCommandDefinition definition) {
        super(definition.command());
        this.plugin = plugin;
        this.qualifiedName = plugin.getContext().getDescription().name() + ":" + getName();
        this.permission = definition.permission();
        this.permissionMessage = definition.permissionMessage();
        this.executor = plugin;
    }

    public AtriPlugin getPlugin() { return plugin; }

    public String getQualifiedName() { return qualifiedName; }

    @Override
    public CommandExecutor getExecutor() { return executor; }

    @Override
    public void setExecutor(CommandExecutor executor) { this.executor = executor == null ? plugin : executor; }

    void setActive(boolean active) { this.active = active; }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!active) return false;
        if (!permission.isBlank() && !sender.hasPermission(permission)) {
            sender.sendMessage(permissionMessage);
            return true;
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(plugin.getClass().getClassLoader());
            boolean success = executor.onCommand(sender, this, commandLabel, args);
            if (!success && !getUsage().isBlank()) sender.sendMessage("指令用法: " + getUsage().replace("<command>", commandLabel));
            return success;
        } catch (Throwable failure) {
            PluginManager.rethrowFatal(failure);
            plugin.getContext().getLogger().log(System.Logger.Level.ERROR, "执行插件指令 " + qualifiedName + " 失败", failure);
            sender.sendMessage("执行命令时发生内部错误，请联系开发者处理！");
            return true;
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
