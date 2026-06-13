package top.yzljc.atribot.functions.official.minecraft;

import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.service.official.CommandButton;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName AtriNetwork
 * @Created_at 2026/05/25
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.minecraft
 */
public final class AtriNetwork extends MinecraftNetwork {

    public AtriNetwork() {
        super(true);
    }

    private static final List<String> ALLOWED_COMMANDS = List.of("tps", "list", "unban", "atri reload display", "reboot 30s", "reboot cancel");

    @Override
    public List<String> extraCommand(CommandSender sender, String[] args) {
        if (args.length > 2 && args[1].equals("unban")) {
            return List.of("pardon " + args[2]);
        }
        return List.of();
    }

    @Override
    public String modifyCommand(CommandSender sender, String[] args) {
        String command = commandsJoin(args).trim();
        if (command.equalsIgnoreCase("reboot 30s")) {
            return command + " 远程重启请求，UUID: " + MinecraftBind.getDataByOpenId(sender.unionOpenId()).uuid();
        }
        return command;
    }

    @Override
    public boolean hasPermission(CommandSender sender, String commandContent) {
        if (sender.hasPermission("rcon.atri")) {
            return true;
        }
        String finalCommandContent = commandContent.trim();
        return ALLOWED_COMMANDS.stream().anyMatch(cmd -> finalCommandContent.equals(cmd) || finalCommandContent.startsWith(cmd + " ")) && (MinecraftBind.getDataByOpenId(sender.unionOpenId()).uuid() != null);
    }

    @Override
    public List<List<CommandButton>> getButtons(CommandSender sender, String server, String[] args) {
        String commandContent = commandsJoin(args).trim();
        return List.of(
                List.of(
                        new CommandButton("c1", "喊话", "/rc " + server + " bc ", false, 3, 2),
                        new CommandButton("c2", "在线人数", "/rc " + server + " list", true, 0, 2),
                        new CommandButton("c3", "TPS", "/rc " + server + " tps", true, 0, 2)
                ),
                List.of(
                        new CommandButton("c4", "解封", "/rc " + server + " unban ", false, 0, 2),
                        new CommandButton("c5", "刷新展示物品", "/rc " + server + " atri reload display", true, 0, 2)
                ),
                List.of(
                        new CommandButton("c6", "重启服务器（30秒）", "/rc " + server + " reboot 30s", true, 3, 2),
                        new CommandButton("c7", "取消重启", "/rc " + server + " reboot cancel", true, 3, 2)
                ),
                List.of(new CommandButton("s1", "重新执行", "/rc " + server + " " + commandContent, true, 1, 2)),
                List.of(new CommandButton("s2", "发送另一个指令", "/rc " + server + " ", false, 1, 2))
        );
    }
}