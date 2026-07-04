package top.yzljc.atribot.configuration;

/**
 * @Author YZ_Ljc_
 * @ClassName ResourcesProperties
 * @Created_at 2026/06/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
public final class ResourcesProperties {

    private ResourcesProperties() {
    }

    private static final String API = Config.getInstance().getApiUrl();

    public static final String DUMP = API + "/v1/atrimeow/dump";

    // @ClassName RconHandler
    public static final String RCON_GUIDE_IMG = "https://res.yzljc.top/images/rcon-shower.png";

    // @ClassName Test, MusicCommand
    public static final String A_SILENT_MIRROR_MP3 = "https://res.yzljc.top/images/a_silent_mirror.mp3";

    // @ClassName MusicCommand
    public static final String BIOME_FEST_MP3 = "https://res.yzljc.top/images/c418.mp3";

    // @ClassName HypixelAnnouncements
    public static final String HYPIXEL_HEADER_IMG = "https://res.yzljc.top/images/hypixel-header.png";

    // @ClassName MinecraftCommand, VersionCheckImpl
    public static final String GRASS_BLOCK_IMG = "https://res.yzljc.top/images/grass-block-img.png";

    // @ClassName ElectricCheck
    public static final String TUFE_LOGO_IMG = "https://res.yzljc.top/images/tufe-logo.png";

    // @ClassName EventRecord
    public static final String WELCOME_IMG = "https://res.yzljc.top/images/welcome-img-w.png";

    // @ClassName MinecraftNetwork
    public static final String CONSOLE_LOGO_IMG = "https://res.yzljc.top/images/console-logo.png";

    // @ClassName DiceImpl
    public static final String SKB_LOGO_IMG = "https://res.yzljc.top/images/skb-logo.png";

    // @ClassName DiceImpl
    public static final String SKB_BANK_LOGO_IMG = "https://res.yzljc.top/images/skb-bank-logo.png";

    // @ClassName DiceImpl （模板，使用时将 <id> 替换为点数）
    public static final String DICE_RENDER_RESULT_IMG_T = "https://res.yzljc.top/images/dice_render_result_<id>.png";

    // @ClassName DiceImpl
    public static final String DICE_RENDER_RESULT_7_IMG = "https://res.yzljc.top/images/dice_render_result_7.png";

    // @ClassName MinecraftNews
    public static final String MC_NEWS_API = API + "/v1/atrimeow/mcnews";

    // @ClassName Calendar(general), Calendar(task)
    public static final String CALENDAR_API = API + "/v1/atrimeow/calendar";

    // @ClassName HelpCommand
    public static final String HELP_API = API + "/v1/atrimeow/help";

    // @ClassName SponsorCommand
    public static final String SPONSORS_API = API + "/v1/atrimeow/sponsors";

    // @ClassName SignCommand
    public static final String GOLD_IMG = "https://res.yzljc.top/images/gold.png";

    // @ClassName HypixelStatus
    public static final String HYPIXEL_STATUS_API = API + "/v1/atrimeow/hypixel-status";

    // @ClassName MinecraftCapes
    public static final String MINECRAFT_CAPES_API = API + "/v1/atrimeow/minecraft-capes";

    // @ClassName HappyNewYear
    public static final String HAPPY_NEW_YEAR_API = API + "/v1/atrimeow/happy-new-year";

    // @ClassName MojangStatus
    public static final String MOJANG_STATUS_API = API + "/v1/atrimeow/mojang-status";

    // @ClassName BanTracker
    public static final String BAN_TRACKER = API + "/v1/atrimeow/bantracker-chart";

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

    // @ClassName ManosabaDate
    public static final String MANOSABA_DATE_IMG = API + "/v1/atrimeow/manosaba-date";

    // @ClassName WebhookServer
    public static final String COMMIT_DISPLAY_API = API + "/v1/atrimeow/commit-display";

    // @ClassName CucumberGirl
    public static final String GIRL_TEXT_IMG = API + "/v1/atrimeow/girl-emoji-text";

    // @ClassName AnanGirlEmoji
    public static final String ANAN_TEXT_IMG = API + "/v1/atrimeow/anan-emoji-text";
}
