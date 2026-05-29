package top.yzljc.atribot.chat.official;

/**
 * @Author YZ_Ljc_
 * @ClassName TC
 * @Created_at 2026/06/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 */
public class TC {
    public static Markdown md(String text) {
        return new Markdown(text);
    }

    public static String img(String url, int width, int height) {
        return String.format("![img #%dpx #%dpx](%s)\n\n", width, height, url);
    }

    public static String img(String alt, String url, int width, int height) {
        return String.format("![%s #%dpx #%dpx](%s)\n\n", alt, width, height, url);
    }
}