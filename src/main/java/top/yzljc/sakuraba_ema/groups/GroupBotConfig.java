package top.yzljc.sakuraba_ema.groups;

import java.util.Objects;

/**
 * Immutable configuration for one group-only QQ bot instance.
 */
public record GroupBotConfig(
        String key,
        boolean enabled,
        String appId,
        String clientSecret,
        String webhookPath
) {

    public GroupBotConfig {
        key = normalizeKey(key);
        appId = normalize(appId);
        clientSecret = normalize(clientSecret);
        webhookPath = normalize(webhookPath);
    }

    public void validateEnabled() {
        if (!enabled) {
            return;
        }
        requireConfigured(appId, "app-id");
        requireConfigured(clientSecret, "client-secret");
        if (webhookPath.isBlank() || !webhookPath.startsWith("/") || "/".equals(webhookPath)) {
            throw new IllegalArgumentException(
                    "qq.groups.%s.webhook-path must be an absolute non-root path".formatted(key));
        }
    }

    private void requireConfigured(String value, String field) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("qq.groups.%s.%s must not be blank".formatted(key, field));
        }
    }

    private static String normalizeKey(String value) {
        String key = normalize(value);
        if (key.isBlank() || key.length() > 64 || !key.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "QQ group bot key must be 1-64 characters matching [A-Za-z0-9_-]+");
        }
        return key;
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

}
