package top.yzljc.atribot.configuration;

/**
 * @Author YZ_Ljc_
 * @ClassName ResourcesProperties
 * @Created_at 2026/06/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
public final class ResourcesProperties {

    private static final String API = Config.getInstance().getApiUrl();

    private static final String UGC_API = Config.getInstance().getApiUrl();

    private static final String US_API = Config.getInstance().getUS_API();

    public static final String DUMP = API + "/v2/atrimeow/image-dump";

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

    // @ClassName EventRecord
    public static final String WELCOME_DEV_IMG = "https://res.yzljc.top/images/welcome-dev-img.png";

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

    // @ClassName FullMessageEnableCommand
    public static final String FULL_MESSAGE_ENABLE_GUIDE = "https://res.yzljc.top/images/enable_full_message_guide.png";

    // @ClassName MinecraftNews
    public static final String MC_NEWS_API = UGC_API + "/v2/atrimeow/mcnews";

    // @ClassName Calendar(general), Calendar(task)
    public static final String CALENDAR_API = UGC_API + "/v2/atrimeow/calendar";

    // @ClassName HelpCommand
    public static final String HELP_API = UGC_API + "/v2/atrimeow/help";

    // @ClassName SponsorCommand
    public static final String SPONSORS_API = UGC_API + "/v2/atrimeow/sponsors";

    // @ClassName SignCommand
    public static final String GOLD_IMG = "https://res.yzljc.top/images/gold.png";

    // @ClassName HypixelStatus
    public static final String HYPIXEL_STATUS_API = UGC_API + "/v2/atrimeow/hypixel-status";

    // @ClassName MinecraftCapes
    public static final String MINECRAFT_CAPES_API = UGC_API + "/v2/atrimeow/minecraft-capes";

    // @ClassName HappyNewYear
    public static final String HAPPY_NEW_YEAR_API = UGC_API + "/v2/atrimeow/happy-new-year";

    // @ClassName MojangStatus
    public static final String MOJANG_STATUS_API = UGC_API + "/v2/atrimeow/mojang-status";

    // @ClassName BanTracker
    public static final String BAN_TRACKER = UGC_API + "/v2/atrimeow/bantracker-chart";

    // @ClassName PlayerProfile
    public static final String PLAYER_CARD_API = UGC_API + "/v2/atrimeow/player-game-stats";

    // @ClassName PlayerProfile
    public static final String PLAYER_ACHIEVEMENTS_NAME_API = "https://www.yzljc.top/data/api/v2/player/achievements/name/{name}";

    // @ClassName PlayerProfile
    public static final String PLAYER_ACHIEVEMENTS_UUID_API = "https://www.yzljc.top/data/api/v2/player/achievements/uuid/{uuid}";

    // @ClassName PlayerProfile
    public static final String PLAYER_FRIENDS_NAME_API = "https://www.yzljc.top/data/api/v2/player/friends/name/{name}";

    // @ClassName PlayerProfile
    public static final String PLAYER_FRIENDS_UUID_API = "https://www.yzljc.top/data/api/v2/player/friends/uuid/{uuid}";

    // @ClassName PingCommand
    public static final String UGC_STATUS_API = UGC_API + "/v2/system/status";

    // @ClassName SkyblockResourceChecker
    public static final String SKB_VERSION_CHECK = "https://api.hypixel.net/v2/resources/packs";

    // @ClassName PlayerProfile
    public static final String PLAYER_WEB_QUERY = "https://www.yzljc.top/mc/query/";

    // @ClassName HelpCommand
    public static final String MINECRAFT_CAPE_EXAMPLE = "https://res.yzljc.top/images/mc-cape-2013.png";

    // @ClassName VerifyMinecraftCommand （使用时将 {uuid} 替换为玩家 UUID）
    public static final String PLAYER_AVATAR_API = "https://www.yzljc.top/data/api/v1/avatar/{uuid}";

    // @ClassName ManosabaDate
    public static final String MANOSABA_DATE_IMG = UGC_API + "/v2/atrimeow/manosaba-date";

    // @ClassName WebhookServer
    public static final String COMMIT_DISPLAY_API = UGC_API + "/v2/atrimeow/commit-display";

    // @ClassName CucumberGirl
    public static final String GIRL_TEXT_IMG = UGC_API + "/v2/atrimeow/girl-emoji-text";

    // @ClassName AnanGirlEmoji
    public static final String ANAN_TEXT_IMG = UGC_API + "/v2/atrimeow/anan-emoji-text";

    // @ClassName PackVersion
    public static final String PACK_VERSION_API = US_API + "/mcmeta/versions/data.json";

    // @ClassName SkyblockResourceChecker
    public static final String SKB_VERSION_IMG_API = UGC_API + "/v2/atrimeow/skyblock-resource-pack";

    // @ClassName Hitokoto
    public static final String HITOKOTO_API = UGC_API + "/v2/atrimeow/hitokoto";

    // @ClassName EarthOnline
    public static final String EARTH_ONLINE_API = UGC_API + "/v2/atrimeow/earth-online";

    // @ClassName LootService
    public static final String LOOTS_API = UGC_API + "/v2/atrimeow/loots";

    // @ClassName LootService
    public static final String LOOTS_DRAW_CARD_API = UGC_API + "/v2/atrimeow/loots/draw-card";

    // @ClassName LootService
    public static final String LOOTS_OVERVIEW_CARD_API = UGC_API + "/v2/atrimeow/loots/overview-card";

    // @ClassName LootAdminClient
    public static final String LOOTS_ADMIN_ITEMS_API = UGC_API + "/v2/atrimeow/loots/admin/items";

    // @ClassName LootAdminClient
    public static final String LOOTS_ITEM_IMAGE_API = UGC_API + "/v2/atrimeow/loots/image";

    // @ClassName MinecraftWhitelistName
    public static final String PLAYER_PROFILE_API = UGC_API + "/v1/mc/profile/info/{uuid}";

    // @ClassName HypixelTNTWizards
    public static final String HYPIXEL_TNT_WIZARDS_API = UGC_API + "/v2/atrimeow/hypixel-tnt-wizards";

    // @ClassName HypixelZombies
    public static final String HYPIXEL_ZOMBIES_API = UGC_API + "/v2/atrimeow/hypixel-zombies";
}
