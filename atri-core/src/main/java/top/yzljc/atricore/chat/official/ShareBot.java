package top.yzljc.atricore.chat.official;

import top.yzljc.atricore.AtriProvider;
import top.yzljc.atricore.service.request.HttpService;

import java.util.Map;

/**
 * QQ 机器人自定义分享链接
 *
 * @Author YZ_Ljc_
 * @ClassName ShareBot
 * @Created_at 2026/10/16
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official
 */
public final class ShareBot {
    private ShareBot() {
    }

    /**
     * 同步请求生成携带回调数据的分享链接，不缓存结果
     *
     * @param callbackData 传给平台 callback_data 字段的业务标识，不得为 null
     * @return 分享链接，请求失败或响应未包含有效链接时返回 null
     * @throws IllegalStateException Provider 尚未初始化、已关闭或 QQ 未启用
     * @throws NullPointerException  callbackData 为 null
     */
    public static String generateShareLink(String callbackData) {
        var provider = AtriProvider.getInstance();
        var body = Map.of("callback_data", callbackData);
        var result = HttpService.postJson(provider.getSettings().getQqApiBaseUrl() + "/v2/generate_url_link",
                body, "Authorization", "QQBot " + provider.getTokenManager().getAccessToken());
        if (result == null) return null;
        var url = result.path("data").path("url");
        return url.isTextual() && !url.textValue().isBlank() ? url.textValue() : null;
    }

    /**
     * 提取平台分享链接最后一个斜杠后的短码，不发起请求
     *
     * @param shareLink 平台返回的分享链接，可为 null
     * @return 分享链接短码，链接为空或最后一个斜杠后无内容时返回 null
     */
    public static String getShareShortCode(String shareLink) {
        if (shareLink == null) return null;
        int lastSlashIndex = shareLink.lastIndexOf('/');
        if (lastSlashIndex == -1 || lastSlashIndex == shareLink.length() - 1) return null;
        return shareLink.substring(lastSlashIndex + 1);
    }
}
