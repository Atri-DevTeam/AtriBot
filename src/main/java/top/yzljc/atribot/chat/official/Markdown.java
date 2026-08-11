package top.yzljc.atribot.chat.official;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
        return "<@" + userOpenId + ">";
    }

    public static String link(String url, String display) {
        return "[" + display + "](" + url + ")";
    }

    /**
     * 群消息发送失败，
     * 错误码：40034106
     * 原因：群消息不支持 @全体成员（qqbot-at-everyone）
     */
    public static String atAll() {
        return "<qqbot-at-everyone />";
    }
}