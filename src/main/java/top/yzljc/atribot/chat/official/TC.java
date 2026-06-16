package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.button.Button;

import java.util.List;

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

    public static Object keyboard(List<List<Button>> buttons) {
        return Atri.getInstance().getChatService().buildButtonKeyboard(buttons);
    }
}