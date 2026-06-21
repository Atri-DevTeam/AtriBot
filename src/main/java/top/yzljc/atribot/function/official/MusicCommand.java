package top.yzljc.atribot.function.official;

import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.Platform;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName MusicCommand
 * @Created_at 2026/06/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class MusicCommand implements CommandExecutor {

    private static final Map<String, String> musicList = Map.of(
            "a_silent_mirror", ResourcesProperties.A_SILENT_MIRROR_MP3
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) return true;
        if (args.length < 1) {
            sender.sendMessage("无效参数");
            return true;
        }

        String name = args[0];
        String url = musicList.get(name);
        if (url == null) {
            sender.sendMessage("无效音乐");
        }
        GroupChat.replyMessage(sender.getGroupId(), sender.getMessageId(), 3, url);
        return true;
    }
}