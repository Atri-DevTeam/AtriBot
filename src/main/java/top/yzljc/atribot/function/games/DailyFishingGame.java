package top.yzljc.atribot.function.games;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.button.Keyboard;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atribot.database.repo.DailyFishingRepository;
import top.yzljc.atribot.function.games.fishing.FishingResult;
import top.yzljc.atribot.function.games.fishing.FishingTable;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Author YZ_Ljc_
 * @ClassName DailyFishingGame
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games
 */
@Slf4j
public final class DailyFishingGame implements CommandExecutor {
    private static final FishingTable table = FishingTable.load();
    private static final DailyFishingRepository repository = new DailyFishingRepository();

    /**
     * 按钓鱼奖励表配置的时区判断用户今日是否已完成结算。
     *
     * @param userOpenId 用户的 QQ 开放平台 ID，群聊与私聊共用
     * @return 今日存在已结算的钓鱼记录时返回 {@code true}，用户 ID 为空时返回 {@code false}
     * @throws IllegalStateException 查询钓鱼记录失败
     */
    public static boolean hasFishedToday(String userOpenId) {
        if (userOpenId == null || userOpenId.isBlank()) {
            return false;
        }
        try {
            return repository.hasFished(userOpenId, LocalDate.now(table.zone()));
        } catch (SQLException e) {
            log.error("查询今日钓鱼状态失败: userId={}", userOpenId, e);
            throw new IllegalStateException("查询今日钓鱼状态失败", e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }
//        if (args.length != 0) {
//            sender.sendMessage("使用 /fish 或 /今日钓鱼，每天可钓鱼一次");
//            return true;
//        }
        ThreadManager.execute(() -> {
            DailyFishingRepository.Settlement settlement;
            try {
                LocalDate date = LocalDate.now(table.zone());
                settlement = repository.catchOnce(qq.getUserId(), date,
                        () -> table.roll(date, ThreadLocalRandom.current()));
            } catch (Exception e) {
                log.error("每日钓鱼结算失败: userId={}", qq.getUserId(), e);
                qq.sendMessage("暂时无法读取钓鱼结果，请稍后重试");
                return;
            }
            ImageDTO banner = null;
            try {
                banner = PreImageGenerate.dump(OpenApi.get("bot.fishing"), bannerRequest(settlement.result()));
                if (banner.isError())
                    log.warn("钓鱼横幅生成失败: userId={}, error={}", qq.getUserId(), banner.errorMessage());
            } catch (Exception e) {
                log.warn("钓鱼横幅暂不可用，使用文字结果: userId={}", qq.getUserId(), e);
            }
            var ks = new Keyboard(
                    List.of(
                            List.of(
                                    new Button("c1", "\uD83C\uDFA3 每日钓鱼", "/fish", ButtonStyle.BLUE, ButtonType.COMMAND),
                                    new Button("c2", "✨ 我的金粒", "/golds", ButtonStyle.BLUE, ButtonType.COMMAND)
                            )
                    )
            );
            qq.sendMessage(TC.md(render(settlement, banner)).setKeyboard(ks));
        });
        return true;
    }

    private static Map<String, Object> bannerRequest(FishingResult result) {
        return Map.of("name", result.name(), "items", result.items(), "enchanted", !result.enchantments().isEmpty());
    }

    private static String render(DailyFishingRepository.Settlement settlement, ImageDTO banner) {
        var result = settlement.result();
        String category = switch (result.category()) {
            case "fish" -> "鱼类";
            case "junk" -> "杂物";
            case "treasure" -> "宝藏";
            default -> result.category();
        };

        String markdown = "**\uD83C\uDF40 你的今日渔获是 ➜**" + "\n\n";

        if (!settlement.created()) {
            markdown = "**\uD83C\uDF40 今天已经钓过鱼了**" + "\n\n";
        }

        if (banner != null && !banner.isError() && banner.width() > 0 && banner.height() > 0) {
            markdown += Markdown.img(banner.url(), banner.width(), banner.height()) + "\n\n";
        }

        markdown += "> 群系：" + result.biomeName() + "\n" +
                "> 渔获：**" + result.name() + "**（" + category + "）\n";

        if (result.durability() != null) {
            var durability = result.durability();
            markdown += "> 耐久：" + durability.remaining() + "/" + durability.maximum() + "\n";
        }

        if (!result.enchantments().isEmpty()) {
            markdown += "\n附魔\n";
            for (var enchantment : result.enchantments()) {
                markdown += "> " + enchantment.name() + " " + roman(enchantment.level()) + "\n";
            }
        }

        markdown += "\n> " + Markdown.img("atribot_pic", RS.get("resource.image.gold"), 16, 16) + "+ " + result.baseGold() + " 金粒（钓鱼奖励）";

        return markdown;
    }

    private static String roman(int level) {
        return level >= 1 && level <= 5 ? new String[]{"I", "II", "III", "IV", "V"}[level - 1] : Integer.toString(level);
    }
}
