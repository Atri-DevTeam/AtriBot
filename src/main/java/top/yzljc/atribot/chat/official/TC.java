package top.yzljc.atribot.chat.official;

import java.util.List;
import java.util.Map;

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

    public static Ark ark(int templateId, List<Map<String, Object>> content) {
        return Ark.of(templateId, content);
    }
}