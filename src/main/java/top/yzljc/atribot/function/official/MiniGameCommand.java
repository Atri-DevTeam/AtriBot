package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.Platform;

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
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) {
            return true;
        }

        Markdown md = TC.md("**小游戏列表**\n\n" +
                "- " + Markdown.enterCommand("/扫雷", "/扫雷") + " - 扫雷小游戏(5x5)\n" +
                "- " + Markdown.enterCommand("/四子旗", "/四子旗") + " - 四子棋小游戏\n" +
                "> 点击指令开始游玩，更多内容正在开发制作中！");
        sender.sendMessage(md);
        return true;
    }
}