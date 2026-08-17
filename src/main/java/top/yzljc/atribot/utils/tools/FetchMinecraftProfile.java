package top.yzljc.atribot.utils.tools;

import org.jetbrains.annotations.Nullable;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.service.request.HttpService;

import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName FetchMinecraftProfile
 * @Created_at 2026/08/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils.tools
 */
public final class FetchMinecraftProfile {

    public static @Nullable Profile find(String var) {
        if (var == null || var.isBlank()) return null;
        var d = HttpService.sendGetRequest(ResourcesProperties.PLAYER_PROFILE_API.replace("{uuid}", var));
        if (var.length() > 16) {
            if (d != null) {
                var name = d.path("data").path("inGameName").asText(null);
                if (name != null) {
                    return new Profile(UUID.fromString(var), name);
                }
            }
        } else {
            if (d != null) {
                var uuid = d.path("data").path("uuid").asText(null);
                if (uuid != null) {
                    return new Profile(UUID.fromString(uuid), var);
                }
            }
        }
        return null;
    }

    public record Profile(UUID uuid, String username) { }
}