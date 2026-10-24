package top.yzljc.atribot.function.command;

import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.chat.official.media.HexColor;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.function.impl.drawitem.LootService;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName LootsCommand
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.loot
 */
public class ItemCommand implements CommandExecutor {
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
//            qq.sendMessage(ImageComponent.imageOf(d.image().url()));

            var loots = LootRepository.getLoots(qq.getUserId());
            Set<String> catalogItemIds = LootService.getCatalog(false).stream()
                    .map(LootService.LootCatalogItem::itemId).collect(Collectors.toSet());

            // 收集进度 xx.xx%
            String collectProgress = getCollectProgress(loots, catalogItemIds);
            // 收集数量
            long collectCount = getCollectCount(loots);

            // 收集种类 xx/xx
            String collectTypes = getCollectTypes(loots, catalogItemIds);

            Keyboard ks = new Keyboard(
                    List.of(
                            List.of(
                                    new Button("每日打卡", "/sign", ButtonStyle.BLUE),
                                    new Button("再次抽取", "/item", ButtonStyle.BLUE),
                                    new Button("查看金粒", "/golds", ButtonStyle.BLUE)
                            )
                    )
            );

            var md = TC.md(Markdown.img(d.image().url(), d.image().width(), d.image().height()) + "\n\n" +
                    Markdown.at(qq.getUserId()) + "\n\n" +
                    "> 收集进度: " + collectProgress + "\n" +
                    "> 收集数量: " + collectCount + "\n" +
                    "> 收集种类: " + collectTypes
            );

            qq.sendMessage(md.setKeyboard(ks), false);

            return true;
        }

        var d = LootService.drawDailyFreeOrPaid(qq.getUserId(), PAID_DRAW_COST);
        if (!d.success()) {
            qq.sendMessage(d.message());
            return true;
        }

        int netCoins = d.refundCoins() - d.costCoins();
        String coinChange = String.format("%+d金粒", netCoins);
        String levelLine = "> " + Markdown.img(RS.get("resource.image.gold"), 16, 16)
                + "当前物品「" + (d.itemName() != null ? d.itemName() : d.itemId()) + "」等级 "
                + Markdown.colored(pickLevelColor(d.count(), d.special()), "Lv." + d.count())
                + " (" + coinChange + ")   "
                + Markdown.enterCommand("/golds", "查看剩余金粒");
        Markdown md = TC.md(Markdown.img(d.image().url(), d.image().width(), d.image().height()) + "\n\n" + levelLine);

        Object keyboards = TC.keyboard(
                List.of(
                        List.of(
                                new Button("c1", "再抽一次", "/item", ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "查看收集进度", "/item bag", ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                )
        );

        qq.sendMessage(md, keyboards);
        return true;
    }

    private static String getCollectProgress(List<LootRepository.LootRecord> loots, Set<String> catalogItemIds) {
        long collected = loots.stream().map(LootRepository.LootRecord::itemId)
                .filter(catalogItemIds::contains).distinct().count();
        double progress = catalogItemIds.isEmpty() ? 0 : collected * 100.0 / catalogItemIds.size();
        return String.format(Locale.ROOT, "%.2f%%", progress);
    }

    private static long getCollectCount(List<LootRepository.LootRecord> loots) {
        return loots.stream().mapToLong(LootRepository.LootRecord::count).sum();
    }

    private static String getCollectTypes(List<LootRepository.LootRecord> loots, Set<String> catalogItemIds) {
        long collected = loots.stream().map(LootRepository.LootRecord::itemId)
                .filter(catalogItemIds::contains).distinct().count();
        return collected + "/" + catalogItemIds.size();
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
