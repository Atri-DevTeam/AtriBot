package top.yzljc.atribot.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName ResourcesProperties
 * @Created_at 2026/06/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
public final class ResourcesProperties {

    private static final Map<String, String> REQUEST = loadRequestProperties();

    private static final String API = Config.getInstance().getApiUrl();

    private static final String UGC_API = Config.getInstance().getApiUrl();

    private static final String US_API = Config.getInstance().getUS_API();

    public static final String DUMP = API + "/v2/atrimeow/image-dump";

    // @ClassName RconHandler
    public static final String RCON_GUIDE_IMG = request("resource.image.rcon-guide");

    // @ClassName Test, MusicCommand
    public static final String A_SILENT_MIRROR_MP3 = request("resource.audio.a-silent-mirror");

    // @ClassName MusicCommand
    public static final String BIOME_FEST_MP3 = request("resource.audio.biome-fest");

    // @ClassName HypixelAnnouncements
    public static final String HYPIXEL_HEADER_IMG = request("resource.image.hypixel-header");

    // @ClassName MinecraftCommand, VersionCheckImpl
    public static final String GRASS_BLOCK_IMG = request("resource.image.grass-block");

    // @ClassName ElectricCheck
    public static final String TUFE_LOGO_IMG = request("resource.image.tufe-logo");

    // @ClassName EventRecord
    public static final String WELCOME_IMG = request("resource.image.welcome");

    // @ClassName EventRecord
    public static final String WELCOME_DEV_IMG = request("resource.image.welcome-dev");

    // @ClassName MinecraftNetwork
    public static final String CONSOLE_LOGO_IMG = request("resource.image.console-logo");

    // @ClassName DiceImpl
    public static final String SKB_LOGO_IMG = request("resource.image.skyblock-logo");

    // @ClassName DiceImpl
    public static final String SKB_BANK_LOGO_IMG = request("resource.image.skyblock-bank-logo");

    // @ClassName DiceImpl （模板，使用时将 <id> 替换为点数）
    public static final String DICE_RENDER_RESULT_IMG_T = request("resource.image.dice-result-template");

    // @ClassName DiceImpl
    public static final String DICE_RENDER_RESULT_7_IMG = request("resource.image.dice-result-seven");

    // @ClassName FullMessageEnableCommand
    public static final String FULL_MESSAGE_ENABLE_GUIDE = request("resource.image.full-message-guide");

    // @ClassName MinecraftNews
    public static final String MC_NEWS_API = UGC_API + "/v2/atrimeow/mcnews";

    // @ClassName Calendar(general), Calendar(task)
    public static final String CALENDAR_API = UGC_API + "/v2/atrimeow/calendar";

    // @ClassName HelpCommand
    public static final String HELP_API = UGC_API + "/v2/atrimeow/help";

    // @ClassName SponsorCommand
    public static final String SPONSORS_API = UGC_API + "/v2/atrimeow/sponsors";

    // @ClassName SignCommand
    public static final String GOLD_IMG = request("resource.image.gold");

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
    public static final String PLAYER_ACHIEVEMENTS_NAME_API = request("request.player.achievements-by-name");

    // @ClassName PlayerProfile
    public static final String PLAYER_ACHIEVEMENTS_UUID_API = request("request.player.achievements-by-uuid");

    // @ClassName PlayerProfile
    public static final String PLAYER_FRIENDS_NAME_API = request("request.player.friends-by-name");

    // @ClassName PlayerProfile
    public static final String PLAYER_FRIENDS_UUID_API = request("request.player.friends-by-uuid");

    // @ClassName PingCommand
//    public static final String UGC_STATUS_API = UGC_API + "/v2/system/status";

    // @ClassName SkyblockResourceChecker
    public static final String SKB_VERSION_CHECK = request("request.hypixel.resource-packs");

    // @ClassName PlayerProfile
    public static final String PLAYER_WEB_QUERY = request("resource.web.player-query");

    // @ClassName HelpCommand
    public static final String MINECRAFT_CAPE_EXAMPLE = request("resource.image.minecraft-cape-example");

    // @ClassName VerifyMinecraftCommand （使用时将 {uuid} 替换为玩家 UUID）
    public static final String PLAYER_AVATAR_API = request("request.player.avatar");

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
    public static final String SKB_PACK_VERSION_API = UGC_API + "/v2/atrimeow/skyblock-resource-pack";

    // @ClassName Hitokoto
    public static final String HITOKOTO_API = UGC_API + "/v2/atrimeow/hitokoto";

    // @ClassName EarthOnline
    public static final String EARTH_ONLINE_API = UGC_API + "/v2/atrimeow/earth-online";

    // @ClassName WeatherCommand
    public static final String WEATHER_API = UGC_API + "/v2/atrimeow/weather";

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

    // @ClassName FetchMinecraftProfile
    public static final String PLAYER_PROFILE_API = UGC_API + "/v1/mc/profile/info/{uuid}";

    public static final String MINECRAFT_MODERATION_API = UGC_API + "/v1";

    // @ClassName FetchMinecraftProfile
    public static final String PLAYER_REVIEWED_PROFILE = UGC_API + "/v1/mc/profile/reviewed/{player}";

    // @ClassName HypixelTNTWizards
    public static final String HYPIXEL_TNT_WIZARDS_API = UGC_API + "/v2/atrimeow/hypixel-tnt-wizards";

    // @ClassName HypixelZombies
    public static final String HYPIXEL_ZOMBIES_API = UGC_API + "/v2/atrimeow/hypixel-zombies";

    private static Map<String, String> loadRequestProperties() {
        Path path = Path.of(Properties.REQUEST);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("缺少请求资源配置文件: " + path.toAbsolutePath());
        }
        try {
            Map<String, String> values = new ObjectMapper().readValue(path.toFile(), new TypeReference<>() {});
            return values == null ? Map.of() : Map.copyOf(values);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取请求资源配置: " + path.toAbsolutePath(), e);
        }
    }

    private static String request(String key) {
        String value = REQUEST.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("request.json 缺少有效配置项: " + key);
        }
        return value.trim();
    }
}
