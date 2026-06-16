package top.yzljc.atribot.chat.official.button;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName PermissionType
 * @Created_at 2026/06/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official.button
 */
@Getter
@AllArgsConstructor
public enum PermissionType {
    SPECIFIC_USER(0),
    ADMIN(1),
    ALL(2);

    private final int code;
}
