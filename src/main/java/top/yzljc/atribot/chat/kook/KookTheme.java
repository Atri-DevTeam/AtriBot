package top.yzljc.atribot.chat.kook;

import java.util.Locale;

/**
 * @Author YZ_Ljc_
 * @ClassName KookTheme
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.kook
 */
public enum KookTheme {
    PRIMARY, SUCCESS, DANGER, WARNING, INFO, SECONDARY, NONE;

    String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

