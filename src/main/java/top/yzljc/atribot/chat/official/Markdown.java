package top.yzljc.atribot.chat.official;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName Markdown
 * @Created_at 2026/06/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 */
@Getter
@AllArgsConstructor
public class Markdown {
    private final String text;

    public static String img(String url, int width, int height) {
        return String.format("![img #%dpx #%dpx](%s)", width, height, url);
    }

    public static String img(String alt, String url, int width, int height) {
        return String.format("![%s #%dpx #%dpx](%s)", alt, width, height, url);
    }

    public static String enterCommand(String command) {
        return "<qqbot-cmd-enter text=\"" + command + "\" />";
    }

    public static String enterCommand(String command, String display) {
        return "<qqbot-cmd-input text=\"" + command + "\" show=\"" + display + "\" reference=\"false\" />";
    }

    public static String enterCommand(String command, String display, boolean reference) {
        return "<qqbot-cmd-input text=\"" + command + "\" show=\"" + display + "\" reference=\"" + reference + "\" />";
    }

    public static String at(String userOpenId) {
        return "<qqbot-at-user id=\"" + userOpenId + "\" />";
    }

    public static String at(CommandSender sender) {
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return "";
        return "<qqbot-at-user id=\"" + sender.getUserId() + "\" />";
    }

    public static String link(String url, String display) {
        return "[" + display + "](" + url + ")";
    }

    public static String atAll() {
        return "<qqbot-at-everyone />";
    }
}