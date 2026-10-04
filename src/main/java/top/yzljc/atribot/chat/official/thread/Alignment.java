package top.yzljc.atribot.chat.official.thread;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName Alignment
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 */
@Getter
@AllArgsConstructor
public enum Alignment {
    LEFT(0),
    MIDDLE(1),
    RIGHT(2);

    private final int value;
}
