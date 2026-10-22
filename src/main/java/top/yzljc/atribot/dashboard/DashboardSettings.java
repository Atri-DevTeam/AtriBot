package top.yzljc.atribot.dashboard;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atricore.core.CoreConfig;

import java.net.URI;

/**
 * Dashboard 的公开地址与 QQ 互联配置
 *
 * @Author YZ_Ljc_
 * @ClassName DashboardSettings
 * @Created_at 2026/10/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.dashboard
 */
public record DashboardSettings(boolean enabled, String origin, String appId, String appKey, String botAppId,
                                String callbackUrl) {
    public DashboardSettings {
        callbackUrl = publicCallbackUrl(callbackUrl == null || callbackUrl.isBlank()
                ? origin == null ? null : origin + "/auth/qq/callback" : callbackUrl);
    }

    public DashboardSettings(boolean enabled, String origin, String appId, String appKey, String botAppId) {
        this(enabled, origin, appId, appKey, botAppId, null);
    }

    public static DashboardSettings current() {
        Config config = Config.getInstance();
        return new DashboardSettings(config.isDashboardEnabled() && config.isMiniappEnabled(),
                publicOrigin(config.getDashboardBaseUrl()), config.getQqConnectAppId(), config.getQqConnectAppKey(),
                CoreConfig.getInstance().getSettings().getQqAppId(), config.getDashboardCallbackUrl());
    }

    public boolean available() {
        return enabled && origin != null && callbackUrl != null && appId != null && !appId.isBlank()
                && appKey != null && !appKey.isBlank() && botAppId != null && !botAppId.isBlank();
    }

    public String entryUrl() {
        return origin + "/dashboard/";
    }

    public boolean separateCallbackOrigin() {
        return !java.util.Objects.equals(callbackUrl, origin + "/auth/qq/callback");
    }

    public boolean secure() {
        return origin != null && origin.startsWith("https://");
    }

    static String publicOrigin(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim());
            boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (!("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !java.util.Set.of("", "/", "/dashboard", "/dashboard/").contains(uri.getRawPath()))
                return null;
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    static String publicCallbackUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim());
            if (!"/auth/qq/callback".equals(uri.getRawPath()) || uri.getRawQuery() != null || uri.getRawFragment() != null)
                return null;
            String callbackOrigin = publicOrigin(uri.getScheme() + "://" + uri.getRawAuthority());
            return callbackOrigin == null ? null : callbackOrigin + "/auth/qq/callback";
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "DashboardSettings[enabled=" + enabled + ", origin=" + origin + ", appId=" + appId + "]";
    }
}
