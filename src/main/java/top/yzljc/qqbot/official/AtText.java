package top.yzljc.qqbot.official;

import top.yzljc.qqbot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName AtText
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official
 */
public class AtText {

    public static String at(String userOpenId) {
        return "<qqbot-at-user id=\"" + userOpenId + "\" /> \n";
    }

    public static String at(CommandSender sender) {
        return "<qqbot-at-user id=\"" + sender.userOpenId() + "\" /> \n";
    }

     public static String atAll() {
        return "<qqbot-at-everyone /> \n";
    }
}