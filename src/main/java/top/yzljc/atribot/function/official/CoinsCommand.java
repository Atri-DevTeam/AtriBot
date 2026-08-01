package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName CoinsCommand
 * @Created_at 2026/07/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class CoinsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) return true;
        var total = LootRepository.getCoins(sender.getUserId());
        Markdown md = TC.md(Markdown.img(ResourcesProperties.SKB_BANK_LOGO_IMG, 20, 20) + " 当前金粒余额为: " + total);
        sender.sendMessage(md);
        return true;
    }
}