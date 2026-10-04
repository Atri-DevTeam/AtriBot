package top.yzljc.atribot.platform.kook;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.auth.AtriAccount;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.User;

import java.util.Optional;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName KookUser
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.kook
 */
public final class KookUser extends User {
    @Getter
    private final JsonNode raw;
    private final KookApiClient api;
    private final boolean admin;

    public KookUser(Platform platform, String userId, JsonNode raw, KookApiClient api, Set<String> adminIds) {
        super(platform, raw.path("bot").asBoolean(false), userId, raw.path("username").asText(userId));
        if (platform != Platform.KOOK_CHANNEL && platform != Platform.KOOK_DM) {
            throw new IllegalArgumentException("KookUser 仅支持 KOOK 频道和私信");
        }
        this.raw = raw;
        this.api = api;
        this.admin = adminIds.contains(userId);
    }

    @Override
    public Optional<AtriAccount> getAccount() {
        return Optional.empty();
    }

    @Override
    public boolean hasPermission() {
        return admin;
    }

    @Override
    public boolean hasPermission(String permission) {
        return admin;
    }

    @Override
    public boolean isBlocked() {
        return raw.path("status").asInt() == 10;
    }

    public String sendMessage(String text) {
        return api.sendMessage(Platform.KOOK_DM, userId, 1, text, null);
    }
}

