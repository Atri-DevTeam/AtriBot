package top.yzljc.qqbot.feature.minecraft;

import top.yzljc.qqbot.botkits.image.DrawMotd;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.util.concurrent.Executors;

public class Motd {

    public static void processCommand(long groupId, String rawMessage) {
        String trimmed = rawMessage.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String arg = parts.length >= 2 ? parts[1].trim() : null;

        if (arg == null || arg.isEmpty()) {
            MessageSender.sendGroupMessage(groupId, "用法: /motd <ip>", null);
            return;
        }

        DrawMotd.HostPort hp = DrawMotd.parseHostPort(arg);
        if (hp == null) {
            MessageSender.sendGroupMessage(groupId, "无效地址，请使用 主机 或 主机:端口，如 mc.hypixel.net 或 mc.hypixel.net:12345");
            return;
        }

        Executors.newSingleThreadExecutor().submit(() -> DrawMotd.fetchAndSendMotd(groupId, hp));
    }
}