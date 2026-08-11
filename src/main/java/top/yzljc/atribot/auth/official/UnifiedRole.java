package top.yzljc.atribot.auth.official;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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