package top.yzljc.sakuraba_ema.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName ForumCode
 * @Created_at 2026/08/15
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.utils
 */
@Getter
@AllArgsConstructor
public enum ForumCode {

    BOT_UPDATE("739215232"),
    MINECRAFT_NEWS("739210805"),
    HYPIXEL_NEWS("739210960"),
    HYPIXEL_SKYBLOCK_NEWS("739211007");

    public static final String GUILD_ID = "82565391648687862";

    private final String channelId;
}
