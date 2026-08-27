package top.yzljc.atribot.function.command;

import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.DiscordCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.command.SlashCommandArguments;
import top.yzljc.atribot.command.SlashCommandExecutor;
import top.yzljc.atribot.function.minecraft.McVersionImpl;
import top.yzljc.atribot.utils.FormatTools;

import java.util.Map;

public final class MinecraftVersionCommand implements CommandExecutor, SlashCommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof QQCommandSender qq) {
            return McVersionImpl.onCommand(qq);
        }
        if (sender instanceof QQGuildCommandSender guild) {
            return McVersionImpl.onCommand(guild);
        }
        return true;
    }

    @Override
    public boolean onSlashCommand(DiscordCommandSender sender, Command command, String label,
                                  SlashCommandArguments args) {
        Map<String, McVersionImpl.VersionInfo> versions = McVersionImpl.checkCurrentVersion();
        McVersionImpl.VersionInfo release = versions.get("release");
        McVersionImpl.VersionInfo snapshot = versions.get("snapshot");

        DiscordEmbed embed = new DiscordEmbed()
                .title("Minecraft 最新版本信息")
                .field("正式版", formatVersion(release), false)
                .field("快照版", formatVersion(snapshot), false);
        sender.sendEmbed(embed);
        return true;
    }

    private static String formatVersion(McVersionImpl.VersionInfo version) {
        if (version == null) return "未知";
        return version.id() + "\n发布于 " + FormatTools.formatIsoTime(version.releaseTime()).trim();
    }
}
