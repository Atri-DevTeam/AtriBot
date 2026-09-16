package top.yzljc.sakuraba_ema.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName ForumCode
 * @Created_at 2026/08/15
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.utils
 * @Description 这是一个私人内容，如有参考请忽略此处的内容
 */
@Getter
@AllArgsConstructor
public enum ForumCode {

    BOT_UPDATE("739215232"),
    MINECRAFT_NEWS("739210805"),
    HYPIXEL_NEWS("739210960"),
    HYPIXEL_SKYBLOCK_NEWS("739211007");

    // 社区频道
    public static final String GUILD_ID = "82565391648687862";

    // 关注 Minecraft 更新频道
    public static final String SUB_MC = "46252201789200393";

    // 关注Minecraft更新频道 - 官网动态
    public static final String SUB_MC_NEWS = "742365987";

    // 关注Minecraft更新频道 - 版本更新动态
    public static final String SUB_MC_NEWS_VERSION = "742365966";

    private final String channelId;
}
