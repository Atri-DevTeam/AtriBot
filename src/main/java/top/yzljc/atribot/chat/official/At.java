package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName AtText
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official
 */
public class At {

    public static String at(String userOpenId) {
        return "<qqbot-at-user id=\"" + userOpenId + "\" /> \n";
    }

    public static String at(CommandSender sender) {
        return "<qqbot-at-user id=\"" + sender.unionOpenId() + "\" /> \n";
    }

     public static String atAll() {
        return "<qqbot-at-everyone /> \n";
    }
}