package top.yzljc.atribot.function.command;

import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.qq.QQBot;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniGameCommand
 * @Created_at 2026/06/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class MiniGameCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }

        Markdown md = TC.md("**小游戏列表**\n\n" +
                "> " + Markdown.enterCommand("/扫雷", "\uD83D\uDCA3 扫雷小游戏(5x6)") + "\n" +
                "> " + Markdown.enterCommand("/井字棋", "❌ 井字棋小游戏(3x3)") + "\n" +
                "> " + Markdown.enterCommand("/反应力测试", "⚡ 反应力小测试") + "\n" +
                "> " + Markdown.enterCommand("/四子棋", "\uD83D\uDD34 四子棋小游戏") + "\n" +
                "> " + Markdown.enterCommand("/幸运轮盘", "\uD83C\uDFA1 幸运轮盘(2-5人)") + "\n" +
                "> " + Markdown.enterCommand("/rsp", "✊ 石头剪刀布小游戏") + "\n" +
                "> " + Markdown.enterCommand("/sound", "\uD83C\uDFB5 Minecraft 听声辨物") + "\n" +
                "---\n\n" +
                "> 点击上方栏目开始游玩，更多内容正在制作中！"
        );

        var keyboard = new Keyboard(
                List.of(
                        List.of(
                                new Button("g1", "排行榜数据", "https://www.yzljc.top/atribot/#games", ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.LINK),
                                new Button("g2", "反馈与建议", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND).setModal("对" + QQBot.BOT_NAME + "的部分内容有更改建议？遇到了问题？欢迎向开发者反馈喵~", "我要反馈", "以后再说")
                        )
                )
        );

        qq.sendMessage(md.setKeyboard(keyboard));
        return true;
    }
}
