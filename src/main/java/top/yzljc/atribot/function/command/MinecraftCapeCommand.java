package top.yzljc.atribot.function.command;

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
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;

import java.util.Map;

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
    public boolean onSlashCommand(DiscordCommandSender sender, Command command, String label,
                                  SlashCommandArguments args) {
        var data = request();
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "Minecraft 披风数据获取失败，请稍后重试。");
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().title("Minecraft 披风状态").image(data.url()));
        return true;
    }

    private static top.yzljc.atribot.function.impl.ImageDTO request() {
        return PreImageGenerate.dump(ResourcesProperties.MINECRAFT_CAPES_API, Map.of());
    }
}
