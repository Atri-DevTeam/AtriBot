package top.yzljc.atribot.chat.official.button;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName ButtonStyle
 * @Created_at 2026/06/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official.button
 */
@Getter
@AllArgsConstructor
public enum ButtonStyle {
    GRAY(0),
    BLUE(1),
    ICON_BUTTON(2),
    RED_BUTTON(3),
    BLUE_WITH_BACKGROUND(4);

    private final int code;
}
