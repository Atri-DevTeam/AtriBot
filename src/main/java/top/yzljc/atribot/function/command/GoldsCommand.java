package top.yzljc.atribot.function.command;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonSize;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.repo.LootRepository;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName CoinsCommand
 * @Created_at 2026/07/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class GoldsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;

        var total = LootRepository.getCoins(qq.getUserId());

        Markdown content = TC.md(Markdown.at(qq.getUserId()) + Markdown.img(ResourcesProperties.SKB_BANK_LOGO_IMG, 20, 20) + " 当前拥有金粒: " + total+
                "，您可以通过以下方式获取金粒，更多方式请查阅" + Markdown.link("https://docs.qq.com/doc/DUHJQVG9VVE5yQU1S", "帮助文档"));

//        Markdown md = TC.md(Markdown.at(qq.getUserId()) + Markdown.img(ResourcesProperties.SKB_BANK_LOGO_IMG, 20, 20) + " 当前拥有金粒: " + total
//                + "，您可以在" + Markdown.link("https://docs.qq.com/doc/DUHJQVG9VVE5yQU1S", "帮助文档") + "中找到获取金粒的方式");

        Object keyboard = TC.keyboard(
                List.of(
                        List.of(
                                new Button("c1", "关注B站", "/bvbind help", ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "小游戏", "/games", ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c3", "每日打卡", "/sign", ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                ), ButtonSize.SMALL
        );

        qq.sendMessage(content, keyboard, false);
        return true;
    }
}