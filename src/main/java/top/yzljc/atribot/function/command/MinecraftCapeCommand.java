package top.yzljc.atribot.function.command;

import java.util.Map;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.DiscordCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.command.SlashCommandArguments;
import top.yzljc.atribot.command.SlashCommandExecutor;
import top.yzljc.atribot.command.SlashCommandSender;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.request.OpenApi;

public final class MinecraftCapeCommand implements CommandExecutor, SlashCommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var data = request();
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "Minecraft 披风数据获取失败，请稍后重试。");
            return true;
        }
        if (sender instanceof QQCommandSender qq) {
            qq.sendMessage(ImageComponent.imageOf(data.url()));
        } else if (sender instanceof QQGuildCommandSender guild) {
            guild.sendMessage(ImageComponent.imageOf(data.url()));
        }
        return true;
    }

    @Override
    public boolean onCommand(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        var data = request(sender.getPlatform());
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "Minecraft 披风数据获取失败，请稍后重试。");
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().title("Minecraft 披风状态").image(data.url()));
        return true;
    }

    private static top.yzljc.atribot.function.impl.ImageDTO request() {
        return PreImageGenerate.dump(OpenApi.get("bot.minecraft.capes"), Map.of());
    }

    private static top.yzljc.atribot.function.impl.ImageDTO request(Platform platform) {
        return PreImageGenerate.dump(OpenApi.get("bot.minecraft.capes"), Map.of(), platform);
    }
}
