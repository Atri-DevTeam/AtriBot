package top.yzljc.atribot.test;

import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName Test
 * @Created_at 2026/06/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
public class Test implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = "https://www.yzljc.top/img/a_silent_mirror.mp3";
        GroupChat.replyMessage(sender.getGroupId(), sender.getMessageId(), 3, url);
        return true;
    }
}