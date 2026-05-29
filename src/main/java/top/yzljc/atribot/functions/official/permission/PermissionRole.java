package top.yzljc.atribot.functions.official.permission;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PermissionRole {

    OWNER("机器人拥有者"),
    ADMIN("管理员"),
    USER("普通用户"),
    BLACKLIST("黑名单");

    private final String displayName;

    public static PermissionRole fromString(String role) {

        try {
            return PermissionRole.valueOf(role.toUpperCase());
        } catch (Exception ignored) {
            return USER;
        }
    }
}