package top.yzljc.atribot.function.command;

import top.yzljc.atribot.auth.UnifiedAccount;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;

/**
 * @Author YZ_Ljc_
 * @ClassName McBindCommand
 * @Created_at 2026/08/31
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public class McBindCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof QQCommandSender user) {
            if (args.length != 1 || args[0].isBlank()) {
                user.sendMessage("喵？没有看到用户名或UUID捏？");
                return true;
            }

            var profile = FetchMinecraftProfile.find(args[0].trim());
            if (profile == null || profile.uuid() == null) {
                user.sendMessage("[!] 未找到该 Minecraft 玩家，请检查用户名或 UUID。");
                return true;
            }

            UnifiedAccount account = UnifiedAuthentication.ensureByQqUserOpenId(user.getUserId(), user.getUsername());
            if (account == null) {
                user.sendMessage("[!] 统一身份认证账号创建失败，请稍后再试！");
                return true;
            }

            String minecraftUuid = profile.uuid().toString();
            if (!UnifiedAuthentication.bindMinecraftUuid(account.uuid(), minecraftUuid)) {
                user.sendMessage("[!] Minecraft UUID 绑定失败，请稍后再试！");
                return true;
            }

            int bindingCount = UnifiedAuthentication.countByMinecraftUuid(minecraftUuid);

            // 送金粒
            if (CoinGainLogRepository.countCoinGains(user.getUserId(), "mc_bind") < 1) {
                LootRepository.addCoins(user.getUserId(), 200, "mc_bind");
            }

            String message = "绑定玩家事宜完毕";
            if (bindingCount > 1) {
                message = message + "，该玩家已被" + bindingCount + "人绑定。";
            } else {
                message = message + "。";
            }
            user.sendMessage(message);
            return true;
        }
        return true;
    }
}
