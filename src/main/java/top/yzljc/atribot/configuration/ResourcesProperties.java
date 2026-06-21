package top.yzljc.atribot.configuration;

/**
 * @Author YZ_Ljc_
 * @ClassName ResourcesProperties
 * @Created_at 2026/06/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
public class ResourcesProperties {

    // ==================== 图片 / 媒体资源 ====================

    // @ClassName RconHandler
    public static final String RCON_GUIDE_IMG = "https://www.yzljc.top/img/rcon-shower.png";

    // @ClassName Test, MusicCommand
    public static final String A_SILENT_MIRROR_MP3 = "https://www.yzljc.top/img/a_silent_mirror.mp3";

    // @ClassName HypixelAnnouncements
    public static final String HYPIXEL_HEADER_IMG = "https://www.yzljc.top/img/hypixel-header.png";

    // @ClassName MinecraftCommand, VersionCheckImpl
    public static final String GRASS_BLOCK_IMG = "https://www.yzljc.top/img/grass-block-img.png";

    // @ClassName ElectricCheck
    public static final String TUFE_LOGO_IMG = "https://www.yzljc.top/img/tufe-logo.png";

    // @ClassName PlayerProfile, VerifyMinecraftCommand
    public static final String HOW_TO_VERIFY_GIF = "https://www.yzljc.top/img/how-to-verify.gif";

    // @ClassName FullMessageEnableCommand
    public static final String FULL_MESSAGE_GUIDE_IMG = "https://www.yzljc.top/img/full-message-guide.png";

    // @ClassName EventRecord
    public static final String WELCOME_IMG = "https://www.yzljc.top/img/welcome-img.png";

    // @ClassName MinecraftNetwork
    public static final String CONSOLE_LOGO_IMG = "https://www.yzljc.top/img/console-logo.png";

    // @ClassName DiceImpl
    public static final String SKB_LOGO_IMG = "https://www.yzljc.top/img/skb-logo.png";

    // @ClassName DiceImpl
    public static final String SKB_BANK_LOGO_IMG = "https://www.yzljc.top/img/skb-bank-logo.png";

    // @ClassName DiceImpl （模板，使用时将 <id> 替换为点数）
    public static final String DICE_RENDER_RESULT_IMG = "https://www.yzljc.top/img/dice_render_result_<id>.png";

    // @ClassName DiceImpl
    public static final String DICE_RENDER_RESULT_7_IMG = "https://www.yzljc.top/img/dice_render_result_7.png";

    // ==================== 接口 / API 资源 ====================

    // @ClassName MinecraftNews
    public static final String MCNEWS_API = "https://www.yzljc.top/data/api/v2/atribot/function/mcnews";

    // @ClassName Calendar(general), Calendar(task)
    public static final String CALENDAR_API = "https://www.yzljc.top/data/api/v2/atribot/function/calendar";

    // @ClassName HelpCommand
    public static final String HELP_API = "https://www.yzljc.top/data/api/v2/atribot/function/help";

    // @ClassName SponsorCommand
    public static final String SPONSORS_API = "https://www.yzljc.top/data/api/v2/atribot/function/sponsors";

    // @ClassName PreImageGenerate
    public static final String IMAGE_DUMP_API = "https://www.yzljc.top/data/api/v2/atribot/function/image-dump";

    // @ClassName HappyNewYear
    public static final String HAPPY_NEW_YEAR_API = "https://www.yzljc.top/data/api/v2/atribot/function/happy-new-year";

    // @ClassName MojangStatus
    public static final String MOJANG_STATUS_API = "https://www.yzljc.top/data/api/v2/atribot/function/mojang-status";

    // @ClassName Feedback
    public static final String FEEDBACK_SUBMIT_API = "https://www.yzljc.top/data/api/v2/open-api/feedback/submit";

    // @ClassName PlayerProfile （使用时拼接 key 与查询参数）
    public static final String PLAYER_CARD_API = "https://www.yzljc.top/data/api/v2/player/card/";

    // @ClassName PlayerProfile
    public static final String PLAYER_ACHIEVEMENTS_NAME_API = "https://www.yzljc.top/data/api/v2/player/achievements/name/{name}";

    // @ClassName PlayerProfile
    public static final String PLAYER_ACHIEVEMENTS_UUID_API = "https://www.yzljc.top/data/api/v2/player/achievements/uuid/{uuid}";

    // @ClassName PlayerProfile
    public static final String PLAYER_FRIENDS_NAME_API = "https://www.yzljc.top/data/api/v2/player/friends/name/{name}";

    // @ClassName PlayerProfile
    public static final String PLAYER_FRIENDS_UUID_API = "https://www.yzljc.top/data/api/v2/player/friends/uuid/{uuid}";

    // @ClassName PlayerProfile
    public static final String PLAYER_GAMESTATS_NAME_API = "https://www.yzljc.top/data/api/v2/player/gamestats/name/{name}";

    // @ClassName PlayerProfile
    public static final String PLAYER_GAMESTATS_UUID_API = "https://www.yzljc.top/data/api/v2/player/gamestats/uuid/{uuid}";

    // @ClassName PlayerProfile
    public static final String PLAYER_WEB_QUERY = "https://www.yzljc.top/mc/query/";

    // @ClassName VerifyMinecraftCommand （使用时将 {uuid} 替换为玩家 UUID）
    public static final String PLAYER_AVATAR_API = "https://www.yzljc.top/data/api/v1/avatar/{uuid}";
}
