package top.yzljc.atribot.platform.discord;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.auth.AtriAccount;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.User;

import java.util.Optional;

/**
 * @Author YZ_Ljc_
 * @ClassName DiscordUser
 * @Created_at 2026/07/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.discord
 */
@Getter
public class DiscordUser extends User {
    private final PlatformRole role;
    private final JsonNode data;
    private final String guildId;
    private final String channelId;
    private final JsonNode raw;

    public DiscordUser(Platform platform, boolean bot, String userId, String username, PlatformRole role, JsonNode data, String guildId, String channelId, JsonNode raw) {
        super(platform, bot, userId, username);
        if (platform != Platform.DISCORD_GUILD && platform != Platform.DISCORD_DM) {
            throw new IllegalArgumentException("DiscordUser 仅支持 Discord");
        }
        this.role = role;
        this.data = data;
        this.guildId = guildId;
        this.channelId = channelId;
        this.raw = raw;
    }

    @Override
    public Optional<AtriAccount> getAccount() {
        return Optional.empty();
    }

    @Override
    public boolean hasPermission() {
        return OfficialUsers.isAdmin(userId);
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasPermission() || OfficialUsers.hasPermission(userId, permission);
    }

    @Override
    public boolean isBlocked() {
        return false;
    }

    public boolean isPlatformAdmin() {
        return role == PlatformRole.ADMIN || role == PlatformRole.OWNER;
    }
}
