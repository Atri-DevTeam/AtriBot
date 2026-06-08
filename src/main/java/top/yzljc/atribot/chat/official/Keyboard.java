package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.service.official.CommandButton;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName Keyboard
 * @Created_at 2026/06/09
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 */
public final class Keyboard {

    public static Object build(List<List<CommandButton>> layout) {
        return Atri.getInstance().getChatService().buildCmdKeyboard(layout);
    }

    public static Object compose(List<List<Button>> layout) {
        return Atri.getInstance().getChatService().buildButtonKeyboard(layout);
    }
}
