package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.discord.DiscordMessage;
import top.yzljc.atribot.platform.discord.DiscordUser;

@Getter
public class DiscordMessageCreateEvent extends Event {
    private final DiscordUser user;
    private final DiscordMessage message;
    private final String guildId;
    private final String channelId;

    public DiscordMessageCreateEvent(DiscordUser user, DiscordMessage message, String guildId, String channelId) {
        this.user = user;
        this.message = message;
        this.guildId = guildId;
        this.channelId = channelId;
    }
}
