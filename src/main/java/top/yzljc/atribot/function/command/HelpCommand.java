package top.yzljc.atribot.function.command;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.platform.qq.QQBot;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName HelpCommand
 * @Created_at 2026/08/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public class HelpCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            Markdown md = TC.md("✨ **" + QQBot.BOT_NAME + "帮助菜单**\n\n" +
                    "> \uD83D\uDCA1小提示: 下方内容可直接点击触发\n\n" +
                    "---\n\n" +
                    "> " + Markdown.enterCommand("/打卡", "\uD83D\uDCCC每日打卡签到") + "\n" +
                    "> " + Markdown.img(ResourcesProperties.CONSOLE_LOGO_IMG, 16, 16) + Markdown.enterCommand("/drawitem", "抽取随机Minecraft物品") + "\n" +
                    "> " + Markdown.img(ResourcesProperties.GRASS_BLOCK_IMG, 16, 16) + Markdown.enterCommand("/mctool", "Minecraft工具二级菜单") + "\n" +
                    "> " + Markdown.img(ResourcesProperties.DICE_RENDER_RESULT_IMG_T.replace("<id>", "6"), 16, 16) + Markdown.enterCommand("/hyp dice", "Skyblock运气测试") + "\n" +
                    "> " + Markdown.img(ResourcesProperties.HYPIXEL_HEADER_IMG, 16, 16) + Markdown.enterCommand("/hyp gs", "查询Hypixel全服在线情况") + "\n" +
                    "> " + Markdown.enterCommand("/cl ", "\uD83C\uDF81领取Hypixel每日签到奖励") + "\n" +
                    "> " + Markdown.enterCommand("/rsp", "✊一场酣畅淋漓的石头剪刀布") + "\n" +
                    "> " + Markdown.enterCommand("/mojang", "\uD83D\uDEE0查询MC验证服务器状态") + "\n" +
                    "> " + Markdown.enterCommand("/newyear", "\uD83E\uDDE8查看新年倒计时") + "\n" +
                    "> " + Markdown.enterCommand("/tasks", "\uD83D\uDD14设置推送任务") + "\n" +
                    "> " + Markdown.img(ResourcesProperties.MINECRAFT_CAPE_EXAMPLE, 16, 16) + Markdown.enterCommand("/mctool cape", "查看MC披风拥有情况") + "\n" +
                    "> " + Markdown.enterCommand("/bantrack", "\uD83D\uDEABHypixel Ban Track"));

            Object keyboard = TC.keyboard(
                    List.of(
                            List.of(
                                    new Button("s1", "问题反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND).setModal("对" + QQBot.BOT_NAME + "的部分内容有更改建议？遇到了问题？欢迎向开发者反馈喵~", "我要反馈", "以后再说"),
                                    new Button("s6", "全量消息", "/全量消息", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                            ),
                            List.of(
//                                    new Button("l1", "社区交流", "https://qm.qq.com/q/UXrrpLsICG", true, ButtonStyle.BLUE, ButtonType.LINK),
                                    new Button("l2", "添加到群", "https://web.qun.qq.com/qunrobot/jump.html?robot_uin=4019803690&target=2", true, ButtonStyle.BLUE, ButtonType.LINK),
                                    new Button("l3", "添加到频道", "https://qun.qq.com/qunpro/robot/share?robot_appid=1904114485", true, ButtonStyle.BLUE, ButtonType.LINK)
                            )
                    )
            );
            user.sendMessage(md, keyboard);
        }

        return true;
    }
}