package top.yzljc.atribot.utils.tools;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.minecraft.MinecraftModerationClient;
import top.yzljc.atribot.service.request.BizResponse;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.request.Requests;

/**
 * @Author YZ_Ljc_
 * @ClassName FetchMinecraftProfile
 * @Created_at 2026/08/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils.tools
 */
public final class FetchMinecraftProfile {
    private static final Pattern SKIN_ID = Pattern.compile("[0-9a-fA-F]{32,128}");

    public static @Nullable Profile find(String var) {
        if (var == null || var.isBlank()) return null;
        return profile(Requests.get(OpenApi.get("minecraft.profile.resolve", "player", var)));
    }

    static @Nullable Profile profile(BizResponse<JsonNode> response) {
        if (!response.isSuccess() || response.data() == null || !response.data().isObject()) return null;
        JsonNode data = response.data();
        String name = data.path("inGameName").asText(null);
        String uuid = data.path("uuid").asText(null);
        if (name == null || name.isBlank() || uuid == null || uuid.isBlank()) return null;
        try {
            return new Profile(parseUuid(uuid), name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        String compact = value == null ? "" : value.replace("-", "");
        if (compact.length() == 32) {
            value = compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-" + compact.substring(12, 16)
                    + "-" + compact.substring(16, 20) + "-" + compact.substring(20);
        }
        return UUID.fromString(value);
    }

    public static String getUsernameByUuid(String uuid) {
        if (uuid.length() <= 16) return uuid;
        Profile profile = find(uuid);
        return profile == null ? uuid : profile.username();
    }

    public static MinecraftProfile getPlayerProfile(String var) {
        if (var == null || var.isBlank()) return null;
        var response = Requests.get(OpenApi.get("minecraft.profile.reviewed", "player", var),
                "Authorization", "API: " + Config.getInstance().getMinecraftModerationReviewKey());
        if (!response.isSuccess() || response.data() == null || !response.data().isObject()) return null;
        var d = response.data();
        return new MinecraftProfile(d.path("username").asText(null),
                d.path("uuid").asText(null),
                reviewedImageUrl("minecraft.skin3d", d.path("skin3dUrl").asText(null)),
                reviewedImageUrl("minecraft.avatar", d.path("avatarUrl").asText(null)));
    }

    private static String reviewedImageUrl(String key, String value) {
        if (value == null || value.isBlank()) return null;
        URI uri = URI.create(value);
        String path = uri.getPath();
        String skinId = path.substring(path.lastIndexOf('/') + 1);
        if (skinId.isBlank()) return null;
        String url = OpenApi.get(key, "skinId", skinId);
        return uri.getRawQuery() == null ? url : url + "?" + uri.getRawQuery();
    }

    /** 返回远端审核策略处理后的玩家名。 */
    public static String getFilteredUserName(String player) {
        Profile profile = find(player);
        String name = profile == null ? player : profile.username();
        var data = MinecraftModerationClient.filterName(name);
        return data.path("value").asText(name);
    }

    /** 返回审核头像 PNG 的 Base64；未通过时远端会返回 Steve fallback。 */
    public static String getFilteredAvatar(String playerOrSkinId) {
        return Base64.getEncoder().encodeToString(MinecraftModerationClient.avatar(resolveSkinId(playerOrSkinId)));
    }

    /** 返回审核 skin3d PNG 的 Base64；未通过时远端会返回 Steve fallback。 */
    public static String getFilteredSkin3d(String playerOrSkinId) {
        return Base64.getEncoder().encodeToString(MinecraftModerationClient.skin3d(resolveSkinId(playerOrSkinId)));
    }

    /** 将名字或 UUID 解析为玩家当前 Mojang 官方皮肤 ID；也接受已经解析好的 skinId。 */
    public static String resolveSkinId(String playerOrSkinId) {
        if (playerOrSkinId == null || playerOrSkinId.isBlank()) throw new IllegalArgumentException("玩家或皮肤 ID 不能为空");
        String value = playerOrSkinId.trim();
        if (SKIN_ID.matcher(value).matches() && value.length() > 36) return value;
        Profile profile = find(value);
        if (profile == null) throw new IllegalArgumentException("无法解析 Minecraft 玩家");
        String uuid = profile.uuid().toString().replace("-", "");
        var session = HttpService.sendGetRequest("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        if (session == null) throw new IllegalArgumentException("无法读取玩家官方皮肤");
        for (var property : session.path("properties")) {
            if (!"textures".equals(property.path("name").asText())) continue;
            try {
                String json = new String(Base64.getDecoder().decode(property.path("value").asText()), StandardCharsets.UTF_8);
                var textures = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                String url = textures.path("textures").path("SKIN").path("url").asText(null);
                if (url != null && url.contains("/texture/")) return url.substring(url.lastIndexOf('/') + 1);
            } catch (Exception ignored) { }
        }
        throw new IllegalArgumentException("玩家没有可用的官方皮肤");
    }

    public record Profile(UUID uuid, String username) { }
}
