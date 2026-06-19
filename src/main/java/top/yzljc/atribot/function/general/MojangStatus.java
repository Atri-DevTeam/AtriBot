package top.yzljc.atribot.function.general;

import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

/**
 * @Author YZ_Ljc_
 * @ClassName MojangStatus
 * @Created_at 2026/06/01
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.overall
 */
public class MojangStatus implements CommandExecutor {

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/mojang-status?key=" + secret + "&" + System.currentTimeMillis();

        if (sender.getPlatform() == Platform.NAPCAT_GROUP) {
            if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "mojang_status")) {
                return true;
            }
        }

        String messageId = sender.sendMessage("正在检查 Mojang 服务状态，请稍候...");

        int code = PreImageGenerate.create(url);
        if (code != 200) {
            sender.sendMessage("检查 Mojang 服务状态失败，API 返回状态码: " + code);
            sender.recall(messageId);
            return true;
        }

        sender.recall(messageId);

        if (sender.getPlatform() == Platform.NAPCAT_GROUP) {
            if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "mojang_status")) {
                return true;
            }
            sender.sendMessage(url, MessageUtils.ImageType.URL);
            return true;
        } else if (sender.getPlatform() == Platform.OFFICIAL_GROUP || sender.getPlatform() == Platform.OFFICIAL_C2C) {
            sender.sendMessage(url, ImageType.URL);
            return true;
        }
        return true;
    }
}