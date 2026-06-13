package top.yzljc.atribot.functions.official;

import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.functions.official.minecraft.AtriNetwork;
import top.yzljc.atribot.functions.official.minecraft.MinecraftNetwork;
import top.yzljc.atribot.functions.official.minecraft.YunLandNetwork;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName NetworkHandler
 * @Created_at 2026/05/23
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
public class RconHandler implements CommandExecutor {

    private static final Map<String, MinecraftNetwork> registeredServer = Map.of(
            "atri", new AtriNetwork(),
            "yl", new YunLandNetwork()
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.replyMarkdown(label, TC.md("""
                    **未输入服务器编号！**
                    
                    该指令专为Minecraft服务器远程执行指令设计，如果您有需求可以通过 `/feedback` 与开发者联系
                    
                    ![show_img #540px #606px](https://www.yzljc.top/img/rcon-shower.png)"""));
            return true;
        }
        String server = args[0];

        if (registeredServer.get(server) != null) {
            return registeredServer.get(server).handleCommand(sender, command, label, args);
        } else {
            sender.replyMarkdown(label, TC.md("无效的服务器编号: `" + server + "`\n\n" +
                    "该指令专为Minecraft服务器远程执行指令设计，如果您有需求可以通过 `/feedback` 与开发者联系\n\n" +
                    "![show_img #540px #606px](https://www.yzljc.top/img/rcon-shower.png)"));
            return true;
        }
    }
}