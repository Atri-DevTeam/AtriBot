package top.yzljc.atribot.plugin;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName AtriPlugin
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 */
public abstract class AtriPlugin implements CommandExecutor {
    private PluginContext context;

    final void initialize(PluginContext context) {
        if (this.context != null) throw new IllegalStateException("插件已经初始化");
        this.context = context;
    }

    public final PluginContext getContext() {
        if (context == null) throw new IllegalStateException("请在 onLoad 或 onEnable 中访问插件上下文");
        return context;
    }

    public void onLoad() throws Exception {}

    public void onEnable() throws Exception {}

    public void onDisable() throws Exception {}

    public final PluginCommand getCommand(String name) { return getContext().getCommand(name); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { return false; }
}
