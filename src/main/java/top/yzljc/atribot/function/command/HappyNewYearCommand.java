package top.yzljc.atribot.function.command;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.OpenApi;

public class HappyNewYearCommand implements CommandExecutor, SlashCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyNewYearCommand.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var data = PreImageGenerate.dump(OpenApi.get("bot.happy-new-year"), Map.of(), sender.getPlatform());

        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("数据获取失败: " + errMsg);
            log.warn("新年倒计时图片获取失败: {}", errMsg);
            return true;
        }

        if (sender instanceof QQCommandSender qq) {
            qq.sendMessage(ImageComponent.imageOf(data.url()));
        } else if (sender instanceof NapcatCommandSender nc) {
            if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "new_year")) return true;
            nc.sendMessage(ImageComponent.imageOf(data.url()));
        } else if (sender instanceof QQGuildCommandSender guild) {
            guild.sendMessage(ImageComponent.imageOf(data.url()));
        }

        return true;
    }

    @Override
    public boolean onCommand(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        var data = PreImageGenerate.dump(OpenApi.get("bot.happy-new-year"), Map.of(), sender.getPlatform());
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "新年倒计时数据获取失败，请稍后重试。");
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().title("新年倒计时").image(data.url()));
        return true;
    }
}
