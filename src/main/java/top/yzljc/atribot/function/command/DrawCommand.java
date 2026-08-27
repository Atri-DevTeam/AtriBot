package top.yzljc.atribot.function.command;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.media.HexColor;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.drawitem.LootService;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName LootsCommand
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.loot
 */
public class DrawCommand implements CommandExecutor {
    private static final int PAID_DRAW_COST = 33;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }

        if (args.length > 0 && args[0].equals("bag")) {
            var d = LootService.renderOverviewCard(qq.getUserId());
            if (!d.success()) {
                qq.sendMessage(d.message());
                return true;
            }
            qq.sendMessage(ImageComponent.imageOf(d.image().url()));
            return true;
        }

        var d = LootService.drawDailyFreeOrPaid(qq.getUserId(), PAID_DRAW_COST);
        if (!d.success()) {
            qq.sendMessage(d.message());
            return true;
        }

        int netCoins = d.refundCoins() - d.costCoins();
        String coinChange = String.format("%+d金粒", netCoins);
        String levelLine = "> " + Markdown.img(ResourcesProperties.GOLD_IMG, 16, 16)
                + "当前物品「" + (d.itemName() != null ? d.itemName() : d.itemId()) + "」等级 "
                + Markdown.colored(pickLevelColor(d.count(), d.special()), "Lv." + d.count())
                + " (" + coinChange + ")   "
                + Markdown.enterCommand("/golds", "查看剩余金粒");
        Markdown md = TC.md(Markdown.img(d.image().url(), d.image().width(), d.image().height()) + "\n\n" + levelLine);

        Object keyboards = TC.keyboard(
                List.of(
                        List.of(
                                new Button("c1", "再抽一次", "/drawitem", ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "查看收集进度", "/drawitem bag", ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                )
        );

        qq.sendMessage(md, keyboards);
        return true;
    }

    private static HexColor pickLevelColor(int count, boolean special) {
        if (special) return new HexColor("#E04A3F");
        if (count >= 15) return new HexColor("#EF6F8F");
        if (count >= 10) return new HexColor("#E9A81E");
        if (count >= 8) return new HexColor("#A855F7");
        if (count >= 5) return new HexColor("#5BB8DE");
        if (count >= 3) return new HexColor("#4CB878");
        return new HexColor("#8E9AA8");
    }
}
