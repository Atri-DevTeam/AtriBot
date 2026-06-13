package top.yzljc.atribot.functions.official.minecraft;

import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.service.official.CommandButton;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName YunLandNetwork
 * @Created_at 2026/05/25
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.minecraft
 */
public final class YunLandNetwork extends MinecraftNetwork {

    public YunLandNetwork() {
        super(false, Config.getInstance().getYunlandHost(), Config.getInstance().getYunlandPort(), Config.getInstance().getYunlandConnectKey());
    }

    @Override
    public List<String> extraCommand(CommandSender sender, String[] args) {
        return List.of();
    }

    @Override
    public String modifyCommand(CommandSender sender, String[] args) {
        return commandsJoin(args);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String commandContent) {
        return sender.hasPermission("rcon.yunland");
    }

    @Override
    public List<List<CommandButton>> getButtons(CommandSender sender, String server, String[] args) {
        String commandContent = commandsJoin(args);
        return List.of(
                List.of(new CommandButton("s1", "重新执行", "/rc " + server + " " + commandContent, true, 1, 2)),
                List.of(new CommandButton("s2", "发送另一个指令", "/rc " + server + " ", false, 1, 2))
        );
    }
}