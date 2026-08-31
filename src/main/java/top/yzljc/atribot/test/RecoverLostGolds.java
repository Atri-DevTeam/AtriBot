package top.yzljc.atribot.test;

import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName RecoverLostGolds
 * @Created_at 2026/08/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
public class RecoverLostGolds implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender qq) {
            var userId = qq.getUserId();
            var groupId = qq.getGroupId() == null ? "none" : qq.getGroupId();
            switch (label) {
                case "recoverjoin" -> {
                    if (CoinGainLogRepository.countCoinGains(userId, "join_my_group") < 1 && groupId.equals("8B4709F81FE02E5E64AC31B2F910793A")) {
                        LootRepository.addCoins(userId, 200, "join_my_group");
                        qq.sendMessage("补发join_my_group奖励200金粒成功！");
                        return true;
                    } else {
                        qq.sendMessage("不符合补发条件！");
                        return true;
                    }
                }
                case "recoverbefriend" -> {
                    if (CoinGainLogRepository.countCoinGains(userId, "friend_add") < 1 && qq.getPlatform().equals(Platform.OFFICIAL_C2C)) {
                        LootRepository.addCoins(userId, 100, "friend_add");
                        qq.sendMessage("补发friend_add奖励100金粒成功！");
                        return true;
                    } else {
                        qq.sendMessage("不符合补发条件（请尝试私聊发送）！");
                        return true;
                    }
                }
            }
            return true;
        }

        if (sender instanceof QQGuildCommandSender guild) {
            var userId = guild.getUserOpenId();
            if (label.equals("recoverguild")) {
                if (CoinGainLogRepository.countCoinGains(userId, "join_guild") < 1) {
                    LootRepository.addCoins(userId, 200, "join_guild");
                    guild.sendMessage("补发join_guild奖励200金粒成功！");
                    return true;
                } else {
                    guild.sendMessage("不符合补发条件（可能是已发放）！");
                    return true;
                }
            }
        }
        return true;
    }
}