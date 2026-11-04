package top.yzljc.atricore.chat.official.button;

import java.util.Objects;

/**
 * 提示词按钮，序列化为 prompt_keyboard.keyboard
 *
 * @param keyboard 键盘内容
 * @Author YZ_Ljc_
 * @ClassName PromptKeyboard
 * @Created_at 2026/11/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official
 */
public record PromptKeyboard(KeyboardPayload keyboard) {
    public PromptKeyboard {
        Objects.requireNonNull(keyboard, "交互区键盘不能为空");
    }
}
