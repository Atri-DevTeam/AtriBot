package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.ShareBot;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialFriendAddEvent;
import top.yzljc.atribot.event.events.OfficialFriendDelEvent;
import top.yzljc.atribot.platform.qq.QQBot;

/**
 * @Author YZ_Ljc_
 * @ClassName ShareBotCommand
 * @Created_at 2026/09/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
@Slf4j
public class ShareBotCommand implements CommandExecutor, Listener {

    private static final Object SHARE_REWARD_LOCK = new Object();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender user)) return true;

        String shareLink = ShareBot.getShareLink(user.getUserId());
        if (shareLink == null) {
            user.sendMessage("分享链接获取失败，请稍后重试");
            return true;
        }

        var md = TC.md("个人分享\n\n通过此方式分享机器人，邀请其他用户添加" + QQBot.BOT_NAME + "为好友后，您可收到 300 金粒的分享奖励，您的分享链接如下:\n\n```\n" +
                shareLink + "\n```");

        user.sendMessage(md);
        return true;
    }

    @EventHandler
    public void onShareBot(OfficialFriendAddEvent event) {
        var userId = event.getUserOpenId();
        var callbackData = event.getSceneParam();
        if (callbackData == null || callbackData.isBlank() || callbackData.equals(userId)) return;

        synchronized (SHARE_REWARD_LOCK) {
            if (!ShareBot.recordInvitation(callbackData, userId)) return;
            var shareRewardCode = "atrimeow_share_" + userId;
            if (CoinGainLogRepository.countCoinGains(callbackData, shareRewardCode) == 0) {
                if (LootRepository.addCoins(callbackData, 300, shareRewardCode) >= 0) {
                    log.info("用户 {} 获得 300 金粒分享奖励，被邀请人 {}", callbackData, userId);
                } else {
                    log.warn("用户 {} 的分享奖励发放失败，被邀请人 {}", callbackData, userId);
                }
            }
        }
    }
}
