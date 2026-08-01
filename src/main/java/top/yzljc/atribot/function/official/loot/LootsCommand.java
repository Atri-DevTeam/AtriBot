package top.yzljc.atribot.function.official.loot;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.platform.Platform;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName LootsCommand
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.loot
 */
public class LootsCommand implements CommandExecutor {
    private static final int PAID_DRAW_COST = 69;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender.getPlatform() != Platform.OFFICIAL_C2C && sender.getPlatform() != Platform.OFFICIAL_GROUP) {
            return true;
        }

        if (args.length > 0 && args[0].equals("bag")) {
            var d = LootService.renderOverviewCard(sender.getUserId());
            if (!d.success()) {
                sender.sendMessage(d.message());
                return true;
            }
            sender.sendMessage(d.image().url(), ImageType.URL);
            return true;
        }

        var d = LootService.drawDailyFreeOrPaid(sender.getUserId(), PAID_DRAW_COST);
        if (!d.success()) {
            sender.sendMessage(d.message());
            return true;
        }

        String duplicatedRefundLine = d.refundCoins() > 0
                ? "> " + Markdown.img(ResourcesProperties.GOLD_IMG, 16, 16) + "由于已获取过该物品，本次获取 " + d.refundCoins() + " 金粒\n"
                : "";
        String costLine = d.freeDraw()
                ? "> " + Markdown.img(ResourcesProperties.GOLD_IMG, 16, 16) + "本次抽取物品不消耗金粒   " + Markdown.enterCommand("/golds", "查看剩余金粒")
                : "> " + Markdown.img(ResourcesProperties.GOLD_IMG, 16, 16) + "本次抽取物品消耗" + d.costCoins() + "金粒   " + Markdown.enterCommand("/golds", "查看剩余金粒");
        Markdown md = TC.md(Markdown.img(d.image().url(), d.image().width(), d.image().height()) + "\n\n" +
                duplicatedRefundLine +
                costLine);

        Object keyboards = TC.keyboard(
                List.of(
                        List.of(
                                new Button("c1", "再抽一次", "/loot", ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "查看收集进度", "/loot bag", ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                )
        );

        sender.sendMessage(md, keyboards);
        return true;
    }
}
