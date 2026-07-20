package top.yzljc.atribot.platform;

/**
 * @Author YZ_Ljc_
 * @ClassName Platform
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform
 */
public enum Platform {
    OFFICIAL_GROUP,
    OFFICIAL_C2C,
    NAPCAT_GROUP,
    NAPCAT_PRIVATE,
    DISCORD_GUILD,
    DISCORD_DM;

    public boolean isDiscordSlashCommand() {
        return this == DISCORD_GUILD || this == DISCORD_DM;
    }
}
