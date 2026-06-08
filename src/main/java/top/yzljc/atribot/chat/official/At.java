package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName AtText
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official
 * @deprecated 使用 {@link Markdown#at(String)}、{@link Markdown#at(CommandSender)} 和 {@link Markdown#atAll()} 代替，新的工具函数不再自带换行符
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