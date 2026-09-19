package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.HttpService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName ShareBot
 * @Created_at 2026/09/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 */
public final class ShareBot {

    private static final String SHARE_BOT_SETTING_ID = "user_share_link";
    private static final ObjectMapper JSON = new ObjectMapper();

    public record ShareSettings(@JsonProperty("share_url") String shareLink,
                                @JsonProperty("invited_user") List<String> users,
                                @JsonProperty("inviter_open_id") String inviterOpenId,
                                @JsonProperty("create_time") String createTime) {
        public ShareSettings {
            users = users == null ? List.of() : List.copyOf(users);
        }
    }

    public static synchronized ShareSettings getShareSettings(String userOpenId) {
        var setting = OfficialUsers.getUserSetting(userOpenId, SHARE_BOT_SETTING_ID);
        return setting == null || setting.isNull()
                ? new ShareSettings(null, List.of(), null, null)
                : JSON.convertValue(setting, ShareSettings.class);
    }

    public static String getShareLink(String userOpenId) {

        var settings = getShareSettings(userOpenId);
        if (settings.shareLink() != null && !settings.shareLink().isBlank()) {
            return settings.shareLink();
        }

        // {"msg":"invalid GetCustomShareJumpUrlReq.CallbackData: value contains invalid strings","retcode":51}
        // 长度限制32，正好是openId的长度哈哈
        Map<String, String> shareCallBackData = Map.of("callback_data", userOpenId);

        var d = HttpService.postJson(Config.getInstance().getQqApiBaseUrl() + "/v2/generate_url_link", shareCallBackData, "Authorization", "QQBot " + Atri.getInstance().getTokenManager().getAccessToken());
        if (d == null) return null;
        var url = d.path("data").path("url");
        if (!url.isTextual() || url.textValue().isBlank()) return null;
        return saveGeneratedLink(userOpenId, url.textValue());
    }

    private static synchronized String saveGeneratedLink(String userOpenId, String shareLink) {
        // 请求期间可能收到邀请事件，重新读取并保留邀请字段。
        var settings = getShareSettings(userOpenId);
        if (settings.shareLink() != null && !settings.shareLink().isBlank()) return settings.shareLink();
        var updated = new ShareSettings(shareLink, settings.users(), settings.inviterOpenId(),
                LocalDateTime.now().toString());
        return OfficialUsers.setUserSetting(userOpenId, SHARE_BOT_SETTING_ID, updated) ? shareLink : null;
    }

    public static synchronized boolean recordInvitation(String inviterOpenId, String invitedOpenId) {
        if (inviterOpenId == null || inviterOpenId.isBlank() || invitedOpenId == null
                || invitedOpenId.isBlank() || inviterOpenId.equals(invitedOpenId)) return false;

        var inviter = getShareSettings(inviterOpenId);
        var invited = getShareSettings(invitedOpenId);
        if (invited.inviterOpenId() != null && !invited.inviterOpenId().isBlank() && !inviterOpenId.equals(invited.inviterOpenId())) return false;

        if (!inviterOpenId.equals(invited.inviterOpenId())) {
            var updated = new ShareSettings(invited.shareLink(), invited.users(), inviterOpenId, invited.createTime());
            if (!OfficialUsers.setUserSetting(invitedOpenId, SHARE_BOT_SETTING_ID, updated)) return false;
        }
        if (!inviter.users().contains(invitedOpenId)) {
            var users = new ArrayList<>(inviter.users());
            users.add(invitedOpenId);
            var updated = new ShareSettings(inviter.shareLink(), users, inviter.inviterOpenId(), inviter.createTime());
            return OfficialUsers.setUserSetting(inviterOpenId, SHARE_BOT_SETTING_ID, updated);
        }
        return true;
    }

    /**
     * 获取分享链接短码，如 vch8t0RMe4
     * @param userOpenId 被查询对象的开放平台 userOpenId
     * @return 分享链接短码，若获取失败则返回 null
     */
    public static String getUserShareShortCode(String userOpenId) {
        String shareLink = getShareLink(userOpenId);
        if (shareLink == null) {
            return null;
        }

        // 解析短码
        int lastSlashIndex = shareLink.lastIndexOf('/');
        if (lastSlashIndex == -1 || lastSlashIndex == shareLink.length() - 1) {
            return null; // 无效的链接格式
        }

        return shareLink.substring(lastSlashIndex + 1);
    }
}
