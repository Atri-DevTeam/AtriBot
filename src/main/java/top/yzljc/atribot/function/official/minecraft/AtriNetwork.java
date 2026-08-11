package top.yzljc.atribot.function.official.minecraft;

import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.QQCommandSender;

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
    public List<String> extraCommand(QQCommandSender sender, String[] args) {
        if (args.length > 2 && args[1].equals("unban")) {
            return List.of("pardon " + args[2]);
        }
        return List.of();
    }

    @Override
    public String modifyCommand(QQCommandSender sender, String[] args) {
        String command = commandsJoin(args).trim();
        if (command.equalsIgnoreCase("reboot 30s")) {
            return command + " 远程重启请求，UUID: " + MinecraftBind.getDataByOpenId(sender.getUserId()).uuid();
        }
        return command;
    }

    @Override
    public boolean hasPermission(QQCommandSender sender, String commandContent) {
        if (commandContent.contains("unban")) {
            return true;
        }
        if (sender.hasPermission("rcon.atri")) {
            return true;
        }
        String finalCommandContent = commandContent.trim();
        return ALLOWED_COMMANDS.stream().anyMatch(cmd -> finalCommandContent.equals(cmd) || finalCommandContent.startsWith(cmd + " ")) && (MinecraftBind.getDataByOpenId(sender.getUserId()).uuid() != null);
    }

    @Override
    public List<List<Button>> getButtons(QQCommandSender sender, String server, String[] args) {
        String commandContent = commandsJoin(args).trim();
        return List.of(
                List.of(
                        new Button("c1", "喊话", "/rc " + server + " bc ", false, ButtonStyle.GRAY, ButtonType.COMMAND),
                        new Button("c2", "在线人数", "/rc " + server + " list", true, ButtonStyle.GRAY, ButtonType.COMMAND),
                        new Button("c3", "TPS", "/rc " + server + " tps", true, ButtonStyle.GRAY, ButtonType.COMMAND)
                ),
                List.of(
                        new Button("c4", "解封", "/rc " + server + " unban ", false, ButtonStyle.GRAY, ButtonType.COMMAND),
                        new Button("c5", "刷新展示物品", "/rc " + server + " atri reload display", true, ButtonStyle.GRAY, ButtonType.COMMAND)
                ),
                List.of(
                        new Button("c6", "重启服务器（30秒）", "/rc " + server + " reboot 30s", true, ButtonStyle.RED, ButtonType.COMMAND),
                        new Button("c7", "取消重启", "/rc " + server + " reboot cancel", true, ButtonStyle.RED, ButtonType.COMMAND)
                ),
                List.of(new Button("s1", "重新执行", "/rc " + server + " " + commandContent, true, ButtonStyle.BLUE, ButtonType.COMMAND)),
                List.of(new Button("s2", "发送另一个指令", "/rc " + server + " ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
        );
    }
}