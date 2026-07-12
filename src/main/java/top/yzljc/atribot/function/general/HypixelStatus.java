package top.yzljc.atribot.function.general;

import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelStatus
 * @Created_at 2026/07/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
public class HypixelStatus implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender.getPlatform() == Platform.NAPCAT_GROUP || sender.getPlatform() == Platform.NAPCAT_PRIVATE) {
            if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "hypixel_status")) {
                return true;
            }
        }

        String msgId = sender.sendMessage("正在检查Hypixel服务器状态，请稍候...");

        try {
            var data = PreImageGenerate.dump(ResourcesProperties.HYPIXEL_STATUS_API + "?key=" + Config.getInstance().getAtribotKeySecret(), Map.of());
            if (data.isError()) {
                sender.sendMessage(data.errorMessage());
                return true;
            }
            if (data.url() == null) {
                sender.sendMessage(Identifier.HANDLER_ERROR);
                return true;
            }

            if (sender.getPlatform() == Platform.NAPCAT_GROUP || sender.getPlatform() == Platform.NAPCAT_PRIVATE) {
                sender.sendMessage(data.url(), MessageUtils.ImageType.URL);
            } else if (sender.getPlatform() == Platform.OFFICIAL_GROUP || sender.getPlatform() == Platform.OFFICIAL_C2C) {
                sender.sendMessage(data.url(), ImageType.URL);
            } else {
                sender.sendMessage(Identifier.UNSUPPORTED_PLATFORM);
            }
        } finally {
            sender.recall(msgId);
        }
        return true;
    }
}