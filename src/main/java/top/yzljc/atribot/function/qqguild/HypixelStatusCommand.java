package top.yzljc.atribot.function.qqguild;

import java.util.Map;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.service.request.OpenApi;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelStatus
 * @Created_at 2026/07/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
public class HypixelStatusCommand implements CommandExecutor, SlashCommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            user.sendMessage(TC.md("> 该指令已弃用，请使用二级指令 " + Markdown.enterCommand("/hyp gs", "/hyp gs") + "查询！"));
            return true;
        }

        if (sender instanceof QQGuildCommandSender user) {
            sender.sendMessage("正在检查Hypixel服务器状态，请稍候...");

            var data = PreImageGenerate.dump(OpenApi.get("bot.hypixel.status"), Map.of());
            if (data.isError()) {
                sender.sendMessage(data.errorMessage());
                return true;
            }
            if (data.url() == null) {
                sender.sendMessage(Identifier.HANDLER_ERROR);
                return true;
            }

            user.sendMessage(ImageComponent.imageOf(data.url()));
        }

        return true;
    }

    @Override
    public boolean onCommand(SlashCommandSender slashSender, Command command, String label, SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        sender.sendMessage("正在检查Hypixel服务器状态，请稍候...");
        var data = PreImageGenerate.dump(OpenApi.get("bot.hypixel.status"), Map.of(), sender.getPlatform());
        if (data.isError()) {
            sender.sendMessage(data.errorMessage());
            return true;
        }
        if (data.url() == null) {
            sender.sendMessage(Identifier.HANDLER_ERROR);
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().image(data.url()));
        return true;
    }
}
