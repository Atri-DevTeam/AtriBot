package top.yzljc.atribot.platform.discord;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.User;

import java.util.List;

@Getter
public class DiscordMessage extends Message {
    private final String guildId;
    private final String channelId;
    private final JsonNode raw;

    public DiscordMessage(Platform platform, String messageId, String content, String timestamp, List<User> mentionedUsers, String guildId, String channelId, JsonNode raw) {
        super(platform, messageId, content, timestamp, mentionedUsers);
        this.guildId = guildId;
        this.channelId = channelId;
        this.raw = raw;
    }
}
