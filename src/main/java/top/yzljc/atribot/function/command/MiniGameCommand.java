package top.yzljc.atribot.function.command;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;

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
                "- " + Markdown.enterCommand("/扫雷", "/扫雷") + " - 扫雷小游戏(5x6)\n" +
                "- " + Markdown.enterCommand("/反应力测试", "/反应力测试") + " - 测试你的反应速度\n" +
                "- " + Markdown.enterCommand("/四子棋", "/四子棋") + " - 四子棋小游戏\n" +
                "- " + Markdown.enterCommand("/幸运轮盘", "/幸运轮盘") + " - 幸运轮盘(2-5人)\n" +
                "- " + Markdown.enterCommand("/rsp", "/石头剪刀布") + " - 石头剪刀布小游戏\n" +
                "> 点击指令开始游玩，更多内容正在开发制作中！");
        qq.sendMessage(md);
        return true;
    }
}