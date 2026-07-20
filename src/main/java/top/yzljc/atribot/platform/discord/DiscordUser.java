package top.yzljc.atribot.platform.discord;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.User;

@Getter
public class DiscordUser extends User {
    private final String guildId;
    private final String channelId;
    private final JsonNode raw;

    public DiscordUser(Platform platform, boolean bot, String userId, String username, PlatformRole role, JsonNode data, String guildId, String channelId, JsonNode raw) {
        super(platform, bot, userId, username, role, data);
        this.guildId = guildId;
        this.channelId = channelId;
        this.raw = raw;
    }
}
