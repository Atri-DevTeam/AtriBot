package top.yzljc.atribot.function.napcat;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.function.napcat.impl.DrawMotd;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;

public class Motd implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "motd")) return true;
        if (args.length != 1) {
            return false;
        }
        String rawMessage = String.join(" ", args);
        DrawMotd.HostPort hp = DrawMotd.parseHostPort(rawMessage);
        if (hp == null) {
            sender.sendMessage("无效地址，请使用ip/ip:port的格式，如 mc.hypixel.net 或 mc.hypixel.net:12345");
            return true;
        }
        ThreadManager.execute(() -> DrawMotd.fetchAndSendMotd(sender.getGroupId(), hp));
        return true;
    }
}
