package top.yzljc.atribot.test;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.official.minecraft.MinecraftRemote;

import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName YunLandSpecialCommand
 * @Created_at 2026/06/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
public class YunLandSpecialCommand implements CommandExecutor {

    private static final Set<String> allowedCommands = Set.of("list", "tps");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (!label.equals("yl")) return true;

        String host = Config.getInstance().getYunlandHost();
        int port = Config.getInstance().getYunlandPort();
        String key = Config.getInstance().getYunlandConnectKey();

        String logs = "";
        if (args.length > 0) {
            if (allowedCommands.contains(args[0])) {
                String commandToSend = args[0];
                logs = MinecraftRemote.sendCommandTo(host, port, key, commandToSend);

                nc.sendMessage("指令执行结果如下:\n\n" + logs);
            } else {
                nc.sendMessage("无效或没有权限的指令设定！");
            }
            return true;
        }
        nc.sendMessage("无效的特殊指令集！");
        return true;
    }
}