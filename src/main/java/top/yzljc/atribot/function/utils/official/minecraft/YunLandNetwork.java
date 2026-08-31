package top.yzljc.atribot.function.utils.official.minecraft;

import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName YunLandNetwork
 * @Created_at 2026/05/25
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.minecraft
 */
@Deprecated(since = "3.2.2")
public final class YunLandNetwork extends MinecraftNetwork {

    public YunLandNetwork() {
        super(false, Config.getInstance().getYunlandHost(), Config.getInstance().getYunlandPort(), Config.getInstance().getYunlandConnectKey());
    }

    @Override
    public List<String> extraCommand(QQCommandSender sender, String[] args) {
        return List.of();
    }

    @Override
    public String modifyCommand(QQCommandSender sender, String[] args) {
        return commandsJoin(args);
    }

    @Override
    public boolean hasPermission(QQCommandSender sender, String commandContent) {
        return sender.hasPermission("rcon.yunland");
    }

    @Override
    public List<List<Button>> getButtons(QQCommandSender sender, String server, String[] args) {
        String commandContent = commandsJoin(args);
        return List.of(
                List.of(new Button("s1", "重新执行", "/rc " + server + " " + commandContent, true, ButtonStyle.BLUE, ButtonType.COMMAND)),
                List.of(new Button("s2", "发送另一个指令", "/rc " + server + " ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
        );
    }
}