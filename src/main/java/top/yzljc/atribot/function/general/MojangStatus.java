package top.yzljc.atribot.function.general;

import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName MojangStatus
 * @Created_at 2026/06/01
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.overall
 */
public class MojangStatus implements CommandExecutor, SlashCommandExecutor {

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String apiUrl = ResourcesProperties.MOJANG_STATUS_API + "?key=" + secret;

        if (sender.getPlatform() == Platform.NAPCAT_GROUP) {
            if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "mojang_status")) {
                return true;
            }
        }

        String messageId = sender.sendMessage("正在检查 Mojang 服务状态，请稍候...");

        var data = PreImageGenerate.dump(apiUrl, Map.of());
        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("检查 Mojang 服务状态失败: " + errMsg);
            if (messageId != null && !messageId.isBlank()) {
                sender.recall(messageId);
            }
            return true;
        }

        if (messageId != null && !messageId.isBlank()) {
            sender.recall(messageId);
        }

        if (sender.getPlatform() == Platform.NAPCAT_GROUP) {
            sender.sendMessage(data.url(), MessageUtils.ImageType.URL);
            return true;
        } else if (sender.getPlatform() == Platform.OFFICIAL_GROUP || sender.getPlatform() == Platform.OFFICIAL_C2C) {
            sender.sendMessage(data.url(), ImageType.URL);
            return true;
        }
        return true;
    }

    @Override
    public boolean onSlashCommand(DiscordSlashCommandSender sender, Command command, String label, SlashCommandArguments args) {
        sender.sendMessage("正在检查 Mojang 服务状态，请稍候...");

        String apiUrl = ResourcesProperties.MOJANG_STATUS_API + "?key=" + secret;

        var data = PreImageGenerate.dump(apiUrl, Map.of());
        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("检查 Mojang 服务状态失败，请稍后重试: " + errMsg);
            return true;
        }

        if (sender.getPlatform().isDiscordSlashCommand()) {
            sender.sendEmbed(new DiscordEmbed().image(data.url()));
        }
        return true;
    }
}