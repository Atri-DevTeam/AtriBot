package top.yzljc.atribot.configuration;

/**
 * @Author YZ_Ljc_
 * @ClassName ResourcesProperties
 * @Created_at 2026/06/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
public final class ResourcesProperties {

    private static final String US_API = Config.getInstance().getUS_API();

    // @ClassName HypixelBanWaveAlertTask
    public static final String HYPIXEL_BANWAVE_API = "https://api.yzljc.top/v1/bantracker/stats/banwave";

    public static final String BILIBILI_BIND_API = Config.getInstance().getBackendApi() + "/v2/atrimeow/bilibili/follow-check";

    // @ClassName PackVersion
    public static final String PACK_VERSION_API = US_API + "/mcmeta/versions/data.json";
}
