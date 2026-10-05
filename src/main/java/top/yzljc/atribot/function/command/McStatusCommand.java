package top.yzljc.atribot.function.command;

import java.util.Map;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.OpenApi;

/**
 * @Author YZ_Ljc_
 * @ClassName MojangStatus
 * @Created_at 2026/06/01
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.overall
 */
public class McStatusCommand implements CommandExecutor, SlashCommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
//        if (sender instanceof NapcatCommandSender nc) {
//            if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "mojang_status")) {
//                return true;
//            }
//        }

        String messageId = sender.sendMessage("正在查询Minecraft验证服务器服务状态，请稍候...");

        var data = PreImageGenerate.dump(OpenApi.get("bot.minecraft.status"), Map.of(), sender.getPlatform());

        try {
            if (data.isError()) {
                String errMsg = data.errorMessage();
                sender.sendMessage("在查询Minecraft验证服务器服务状态时出现错误: " + errMsg);
                return true;
            }
        } finally {
            if (messageId != null && !messageId.isBlank()) {
                if (sender instanceof QQCommandSender qq) {
                    qq.recall(messageId);
                } else if (sender instanceof KookCommandSender kook) {
                    kook.recall(messageId);
                }
            }
        }

        switch (sender) {
//            case NapcatCommandSender nc -> nc.sendMessage(ImageComponent.imageOf(data.url()));
            case QQCommandSender qq -> qq.sendMessage(ImageComponent.imageOf(data.url()));
            case KookCommandSender kook -> kook.sendMessage(ImageComponent.imageOf(data.url()));
            case QQGuildCommandSender guildUser -> guildUser.sendMessage(ImageComponent.imageOf(data.url()));
            default -> {}
        }
        return true;
    }

    @Override
    public boolean onCommand(SlashCommandSender slashSender, Command command, String label, SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;

        sender.sendMessage("正在查询Minecraft验证服务器服务状态，请稍候...");
        var data = PreImageGenerate.dump(OpenApi.get("bot.minecraft.status"), Map.of(), sender.getPlatform());

        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("在查询Minecraft验证服务器服务状态时出现错误: " + errMsg);
            return true;
        }

        sender.sendEmbed(new DiscordEmbed().image(data.url()));

        return true;
    }
}
