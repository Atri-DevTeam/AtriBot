package top.yzljc.atribot.function.general;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName MojangStatus
 * @Created_at 2026/06/01
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.overall
 */
public class MojangStatus implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof NapcatCommandSender nc) {
            if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "mojang_status")) {
                return true;
            }
        }

        String messageId = sender.sendMessage("正在检查 Mojang 服务状态，请稍候...");

        var data = PreImageGenerate.dump(ResourcesProperties.MOJANG_STATUS_API, Map.of());

        try {
            if (data.isError()) {
                String errMsg = data.errorMessage();
                sender.sendMessage("检查 Mojang 服务状态失败: " + errMsg);
                return true;
            }
        } finally {
            if (messageId != null && !messageId.isBlank()) {
                if (sender instanceof NapcatCommandSender nc) {
                    nc.recall(messageId);
                } else if (sender instanceof QQCommandSender qq) {
                    qq.recall(messageId);
                }
            }
        }

        if (sender instanceof NapcatCommandSender nc) {
            nc.sendMessage(data.url(), MessageUtils.ImageType.URL);
            return true;
        } else if (sender instanceof QQCommandSender qq) {
            qq.sendMessage(data.url(), ImageType.URL);
            return true;
        }
        return true;
    }
}