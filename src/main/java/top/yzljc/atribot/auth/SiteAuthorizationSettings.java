package top.yzljc.atribot.auth;

import top.yzljc.atribot.utils.YamlConfiguration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点授权的接入方配置
 *
 * @Author YZ_Ljc_
 * @ClassName SiteAuthorizationSettings
 * @Created_at 2026/10/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public record SiteAuthorizationSettings(boolean enabled, Map<String, Client> clients) {
    public SiteAuthorizationSettings {
        clients = Map.copyOf(clients);
    }

    public record Client(String secret, List<String> redirectUris, String patchUrl) {
        public Client(String secret, List<String> redirectUris) {
            this(secret, redirectUris, null);
        }

        public Client {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
                throw new IllegalArgumentException("站点授权密钥至少需要 32 字节");
            redirectUris = List.copyOf(redirectUris);
            if (redirectUris.isEmpty() || redirectUris.stream().anyMatch(url -> !validRedirectUri(url)))
                throw new IllegalArgumentException("站点授权返回地址必须为不带查询参数的 HTTPS 地址");
            patchUrl = patchUrl == null || patchUrl.isBlank() ? null : patchUrl;
            if (patchUrl != null && !validRedirectUri(patchUrl))
                throw new IllegalArgumentException("站点变更接口必须为不带查询参数的 HTTPS 地址");
        }

        @Override
        public String toString() {
            return "Client[redirectUris=" + redirectUris + ", patchUrl=" + patchUrl + "]";
        }
    }

    /**
     * @param yaml 运行服务配置
     * @return 已校验的接入方配置，未启用时返回空配置
     */
    public static SiteAuthorizationSettings load(YamlConfiguration yaml) {
        if (!yaml.getBoolean("dashboard.authorization.enabled", false))
            return new SiteAuthorizationSettings(false, Map.of());
        Map<String, Client> clients = new HashMap<>();
        var section = yaml.getSection("dashboard.authorization.sites");
        if (section != null) {
            for (String id : section.getKeys()) {
                if (!id.matches("[A-Za-z0-9_-]{1,64}"))
                    throw new IllegalArgumentException("站点授权标识格式无效");
                String prefix = "dashboard.authorization.sites." + id;
                clients.put(id, new Client(yaml.getString(prefix + ".secret", ""),
                        yaml.getStringList(prefix + ".redirect-uris"), yaml.getString(prefix + ".patch-url", "")));
            }
        }
        return new SiteAuthorizationSettings(true, clients);
    }

    static boolean validRedirectUri(String value) {
        if (value == null || value.length() > 2048 || value.chars().anyMatch(c -> c <= 32 || c == 127)) return false;
        try {
            URI uri = URI.create(value);
            boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            return ("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
