package top.yzljc.atricore.auth.official;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 机器人账号角色，与平台群主及管理员角色分别处理
 *
 * @Author YZ_Ljc_
 * @ClassName UnifiedRole
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.auth.official
 */
@Getter
@RequiredArgsConstructor
public enum UnifiedRole {

    OWNER("开发者"),
    ADMIN("管理员"),
    USER("普通用户");

    private final String displayName;

    public static UnifiedRole fromString(String role) {

        try {
            return UnifiedRole.valueOf(role.toUpperCase());
        } catch (Exception ignored) {
            return USER;
        }
    }
}