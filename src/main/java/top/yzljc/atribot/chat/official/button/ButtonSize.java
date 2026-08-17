package top.yzljc.atribot.chat.official.button;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName ButtonSize
 * @Created_at 2026/08/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.button
 */
@Getter
@AllArgsConstructor
public enum ButtonSize {
    SMALL("small"),
    UNDEFINED("undefined");

    private final String size;
}
