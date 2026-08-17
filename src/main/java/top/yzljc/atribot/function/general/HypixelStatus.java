package top.yzljc.atribot.function.general;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Identifier;
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

        if (sender instanceof NapcatCommandSender nc) {
            if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "hypixel_status")) {
                return true;
            }
        }

        String msgId = sender.sendMessage("正在检查Hypixel服务器状态，请稍候...");

        var data = PreImageGenerate.dump(ResourcesProperties.HYPIXEL_STATUS_API, Map.of());
        try {
            if (data.isError()) {
                sender.sendMessage(data.errorMessage());
                return true;
            }
            if (data.url() == null) {
                sender.sendMessage(Identifier.HANDLER_ERROR);
                return true;
            }
        } finally {
            if (sender instanceof NapcatCommandSender nc) {
                nc.recall(msgId);
            } else if (sender instanceof QQCommandSender qq) {
                qq.recall(msgId);
            }
        }

        switch (sender) {
            case NapcatCommandSender nc -> nc.sendMessage(ImageComponent.imageOf(data.url()));
            case QQCommandSender qq -> qq.sendMessage(ImageComponent.imageOf(data.url()));
            case QQGuildCommandSender guildUser -> guildUser.sendMessage(ImageComponent.imageOf(data.url()));
            default -> sender.sendMessage(Identifier.UNSUPPORTED_PLATFORM);
        }

        return true;
    }
}