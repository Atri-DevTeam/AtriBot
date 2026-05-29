package top.yzljc.atribot.feature.minecraft;

import top.yzljc.atribot.service.image.DrawMotd;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;

public class Motd implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return false;
        }
        String rawMessage = String.join(" ", args);
        DrawMotd.HostPort hp = DrawMotd.parseHostPort(rawMessage);
        if (hp == null) {
            sender.reply("无效地址，请使用ip/ip:port的格式，如 mc.hypixel.net 或 mc.hypixel.net:12345", false);
            return true;
        }
        ThreadManager.execute(() -> DrawMotd.fetchAndSendMotd(sender.groupId(), hp));
        return true;
    }
}