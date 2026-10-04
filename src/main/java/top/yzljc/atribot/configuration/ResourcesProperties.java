package top.yzljc.atribot.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @Author YZ_Ljc_
 * @ClassName ResourcesProperties
 * @Created_at 2026/06/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
public final class ResourcesProperties {

    private static final Map<String, String> REQUEST = loadRequestProperties();

    private static final String UGC_API = Config.getInstance().getApiUrl();

    private static final String US_API = Config.getInstance().getUS_API();

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

    // @ClassName SignCommand
    public static final String GOLD_IMG = request("resource.image.gold");

    // @ClassName HypixelBanWaveAlertTask
    public static final String HYPIXEL_BANWAVE_API = "https://api.yzljc.top/v1/bantracker/stats/banwave";

    // @ClassName PingCommand
//    public static final String UGC_STATUS_API = UGC_API + "/v2/system/status";

    public static final String BILIBILI_BIND_API = Config.getInstance().getBackendApi() + "/v2/atrimeow/bilibili/follow-check";

    // @ClassName SkyblockResourceChecker
    public static final String SKB_VERSION_CHECK = request("request.hypixel.resource-packs");

    public static final String MINECRAFT_CAPE_EXAMPLE = request("resource.image.minecraft-cape-example");

    // @ClassName PackVersion
    public static final String PACK_VERSION_API = US_API + "/mcmeta/versions/data.json";

    public static final String ICON_DIAMOND_PICKAXE = request("resource.image.icon-diamond-pickaxe");

    public static final String ICON_KNOWLEDGE_BOOK = request("resource.image.icon-knowledge-book");

    public static final String ICON_ZOMBIE_HEAD = request("resource.image.icon-zombie-head");

    public static final String ICON_TNT = request("resource.image.icon-tnt");

    public static final String ICON_SKYBLOCK_DUNGEON = request("resource.image.icon-skyblock-dungeon");

    public static final String ICON_FISHING_ROD = request("resource.image.icon-fishing-rod");

    public static final String ICON_HOTF = request("resource.image.icon-hotf");

    public static final String ICON_PARKOUR = request("resource.image.icon-parkour");

    public static final String ICON_DROPPER = request("resource.image.icon-dropper");

    public static final String MAINTENANCE_IMG = request("resource.image.maintenance");

    public static final String ICON_BEDWARS = request("resource.image.icon-bedwars");

    public static final String ICON_SKYBLOCK_CALENDAR = request("resource.image.icon-skyblock-calendar");

    public static final String ICON_SLUMBER_HOTEL = request("resource.image.icon-slumber-hotel");

    public static final String ICON_SKYBLOCK_HOTM = request("resource.image.icon-skyblock-hotm");

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
