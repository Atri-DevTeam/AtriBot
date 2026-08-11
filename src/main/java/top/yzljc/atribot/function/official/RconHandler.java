package top.yzljc.atribot.function.official;

import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.official.minecraft.AtriNetwork;
import top.yzljc.atribot.function.official.minecraft.MinecraftNetwork;
import top.yzljc.atribot.function.official.minecraft.YunLandNetwork;
import top.yzljc.atribot.platform.Platform;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName NetworkHandler
 * @Created_at 2026/05/23
 * @Project AtriBot
 * @Package top.yzljc.atribot.official.function
 */
public class RconHandler implements CommandExecutor {

    private static final Map<String, MinecraftNetwork> registeredServer = Map.of(
            "atri", new AtriNetwork(),
            "yl", new YunLandNetwork()
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;
        if (label.equals("unbanme")) {
            qq.sendMessage(TC.md("该指令已做出调整，请点击下方指令\n\n点击输入: " + Markdown.enterCommand("/rc atri unban ", "解封账号")));
            return true;
        }
        if (args.length < 1) {
            qq.sendMessage(TC.md(String.format("""
                    **未输入服务器编号！**

                    该指令专为Minecraft服务器远程执行指令设计，如果您有需求可以通过 `/feedback` 与开发者联系

                    ![show_img #540px #606px](%s)""", ResourcesProperties.RCON_GUIDE_IMG)));
            return true;
        }
        String server = args[0];

        if (registeredServer.get(server) != null) {
            if (qq.getPlatform() == Platform.OFFICIAL_GROUP && !OfficialGroups.isWhitelist(qq.getGroupId())) {
                qq.sendMessage("执行操作失败: 群聊不在白名单内，拒绝执行操作！");
                return true;
            }
            if (qq.getPlatform() == Platform.OFFICIAL_C2C && !qq.hasPermission()) {
                qq.sendMessage("执行操作失败: 用户不在白名单内，拒绝执行操作！");
                return true;
            }
            return registeredServer.get(server).handleCommand(qq, command, label, args);
        } else {
            String maskedServer = "*".repeat(server.length());
            qq.sendMessage(TC.md("无效的服务器编号: `" + maskedServer + "`\n\n" +
                    "该指令专为Minecraft服务器远程执行指令设计，如果您有需求可以通过 `/feedback` 与开发者联系\n\n" +
                    "![show_img #540px #606px](" + ResourcesProperties.RCON_GUIDE_IMG + ")"));
            return true;
        }
    }
}