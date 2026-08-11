package top.yzljc.atribot.auth;

/**
 * @Author YZ_Ljc_
 * @ClassName AccountStatus
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    BANNED,
    DELETED;

    public static AccountStatus fromString(String status) {
        try {
            return AccountStatus.valueOf(status.toUpperCase());
        } catch (Exception ignored) {
            return ACTIVE;
        }
    }
}
