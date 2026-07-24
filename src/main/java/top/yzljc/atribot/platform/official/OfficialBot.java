package top.yzljc.atribot.platform.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.utils.tools.Alert;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialBot
 * @Created_at 2026/07/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.official
 */
@Slf4j
public final class OfficialBot {

    // 注意，这个B玩意是union_id不是user_openid，可能为null
    public static String BOT_UNIONID;

    public static String BOT_AVATAR_URL;

    public static String BOT_NAME;

    public static String BOT_SHARE_LINK;

    public static String BOT_WELCOME_MSG;

    public static void fetchBotInfo() {
        var url = Config.getInstance().getQqApiBaseUrl() + "/users/@me";
        var d = HttpService.sendGetRequest(url, "Authorization", "QQBot " + Atri.getInstance().getTokenManager().getAccessToken());
        if (d == null) {
            d = HttpService.sendGetRequest(url);
            log.warn("获取机器人信息失败，尝试重新获取一次");
        }
        if (d == null) {
            log.error("获取机器人信息失败，请检查网络连接或API服务是否正常");
            Alert.notify("获取机器人信息失败，请检查网络连接或API服务是否正常");
            return;
        }

        BOT_UNIONID = d.path("union_openid").asText(null);
        BOT_AVATAR_URL = d.path("avatar").asText(null);
        BOT_NAME = d.path("username").asText(null);
        BOT_SHARE_LINK = d.path("share_url").asText(null);
        BOT_WELCOME_MSG = d.path("welcome_msg").asText(null);

        log.info("Fetched bot info: unionid={}, avatar_url={}, name={}, share_link={}, welcome_msg={}",
                BOT_UNIONID, BOT_AVATAR_URL, BOT_NAME, BOT_SHARE_LINK, BOT_WELCOME_MSG);
    }
}