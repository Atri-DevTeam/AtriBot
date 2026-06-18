package top.yzljc.atribot.platform;

/**
 * @Author YZ_Ljc_
 * @ClassName PlatformRole
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform
 */
public enum PlatformRole {
    OWNER,
    ADMIN,
    MEMBER;

    public static PlatformRole getPlatformRole(String data) {
        switch (data) {
            case "owner" -> {
                return OWNER;
            }
            case "admin" -> {
                return ADMIN;
            }
            case "member" -> {
                return MEMBER;
            }
        }
        return MEMBER;
    }
}
