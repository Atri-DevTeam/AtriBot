package top.yzljc.qqbot.feature.minecraft;

import top.yzljc.qqbot.botservice.image.DrawMotd;
import top.yzljc.qqbot.botservice.thread.ThreadManager;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;

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