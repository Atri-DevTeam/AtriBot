package top.yzljc.atribot.function.command;

import top.yzljc.atribot.auth.official.OfficialUsers;
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
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.database.repo.SignRepository;
import top.yzljc.atribot.function.games.DailyFishingGame;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName GoldsCommand
 * @Created_at 2026/07/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public class GoldsCommand implements CommandExecutor {
    /**
     * @param userOpenId 用户的 QQ 开放平台 ID
     * @return 用户今日已打卡时返回 {@code true}
     */
    public static boolean hasCheckedInToday(String userOpenId) {
        return SignRepository.hasCheckedInToday(userOpenId);
    }

    /**
     * 根据绑定及奖励记录判断是否已完成 B 站关注，不查询实时关注状态。
     *
     * @param userOpenId 用户的 QQ 开放平台 ID
     * @return 存在 B 站绑定记录或已领取关注奖励时返回 {@code true}
     */
    @Deprecated
    public static boolean hasFollowedBilibili(String userOpenId) {
        var binding = OfficialUsers.getUserSetting(userOpenId, OfficialUsers.BILIBILI_UID_SETTING);
        return (binding != null && !binding.isNull())
                || CoinGainLogRepository.countCoinGains(userOpenId, "bv_follow") > 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;

        var total = LootRepository.getCoins(qq.getUserId());

        Markdown content = TC.md(Markdown.at(qq.getUserId()) + Markdown.img(RS.get("resource.image.skyblock-bank-logo"), 20, 20) + " 当前拥有金粒: " + total+
                "，您可以通过以下方式获取金粒，更多方式请查阅" + Markdown.link("https://docs.qq.com/doc/DUHJQVG9VVE5yQU1S", "帮助文档"));

//        Markdown md = TC.md(Markdown.at(qq.getUserId()) + Markdown.img(RS.get("resource.image.skyblock-bank-logo"), 20, 20) + " 当前拥有金粒: " + total
//                + "，您可以在" + Markdown.link("https://docs.qq.com/doc/DUHJQVG9VVE5yQU1S", "帮助文档") + "中找到获取金粒的方式");

        Button fishBtn = DailyFishingGame.hasFishedToday(qq.getUserId()) ? new Button("c1", "✅ 已完成", "/fish", false, ButtonStyle.RED, ButtonType.CUSTOM).setUnsupportedTip("您今天已经完成钓鱼了！") : new Button("c1", "每日钓鱼", "/fish", ButtonStyle.BLUE, ButtonType.COMMAND);
        Button signBtn = hasCheckedInToday(qq.getUserId()) ? new Button("c3", "✅ 已打卡", "/sign ", false, ButtonStyle.RED, ButtonType.CUSTOM).setUnsupportedTip("您今日已完成打卡，明天再来吧！") : new Button("c3", "每日打卡", "/sign ", true, ButtonStyle.BLUE, ButtonType.COMMAND);

        Object keyboard = TC.keyboard(
                List.of(
                        List.of(
                                fishBtn,
                                new Button("c2", "小游戏", "/games", ButtonStyle.BLUE, ButtonType.COMMAND),
                                signBtn
                        )
                ), ButtonSize.SMALL
        );

        qq.sendMessage(content, keyboard, false);
        return true;
    }
}