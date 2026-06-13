package top.yzljc.atribot.chat.official.button;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName ButtonType
 * @Created_at 2026/06/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official.button
 */
@Getter
@AllArgsConstructor
public enum ButtonType {
    COMMAND(2),
    CALLBACK(1),
    LINK(0);

    private final int code;
}
