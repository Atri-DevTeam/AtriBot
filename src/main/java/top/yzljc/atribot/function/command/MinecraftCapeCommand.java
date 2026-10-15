package top.yzljc.atribot.function.command;

import java.util.LinkedHashMap;
import java.util.Map;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.command.SlashCommandArguments;
import top.yzljc.atribot.command.SlashCommandExecutor;
import top.yzljc.atribot.command.SlashCommandSender;
import top.yzljc.atribot.function.background.BackgroundService;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.request.OpenApi;

public final class MinecraftCapeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var data = request(sender.getPlatform(), sender instanceof QQCommandSender ? sender.getUserId() : null);
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "Minecraft 披风数据获取失败，请稍后重试。");
            return true;
        }
        switch (sender) {
            case QQCommandSender qq -> qq.sendMessage(ImageComponent.imageOf(data.url()));
            case QQGuildCommandSender guild -> guild.sendMessage(ImageComponent.imageOf(data.url()));
            default -> {
            }
        }
        return true;
    }

    private static top.yzljc.atribot.function.impl.ImageDTO request(Platform platform, String userId) {
        Map<String, Object> request = new LinkedHashMap<>();
        BackgroundService.applyApprovedBackground(request, userId);
        return PreImageGenerate.dump(OpenApi.get("bot.minecraft.capes"), request, platform);
    }
}
