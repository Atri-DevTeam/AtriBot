package top.yzljc.atribot.function.command;

import top.yzljc.atribot.configuration.RS;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;

/**
 * 友情链接菜单
 */
public class OtherBotCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;

        qq.sendMessage(TC.md("""
                **逛逛其他机器人～ (｡•̀ᴗ-)✧**
                
                ---
                %s %s
                
                %s %s
                """.formatted(
                Markdown.img(RS.get("bot.lishijie"), 20, 20), Markdown.link("https://qun.qq.com/qunpro/robot/qunshare?robot_appid=102053712&robot_uin=2854208604", "离世界"),
                Markdown.img(RS.get("bot.xiaozhuqunguan"), 20, 20), Markdown.link("https://qun.qq.com/qunpro/robot/qunshare?robot_appid=1905396224&robot_uin=4016673447", "小猪群管")
        ).strip()), false);
        return true;
    }
}
