package top.yzljc.atribot.event.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import top.yzljc.atribot.command.SlashCommandArguments;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.discord.DiscordUser;
import top.yzljc.atribot.service.request.HttpService;

@Getter
public class DiscordSlashCommandEvent extends Event {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DiscordUser user;
    private final String applicationId;
    private final String interactionId;
    private final String token;
    private final String guildId;
    private final String channelId;
    private final String commandName;
    private final JsonNode options;
    private final JsonNode resolved;
    private final String timestamp;
    private final JsonNode raw;
    private final SlashCommandArguments args;
    private boolean responded;

    public DiscordSlashCommandEvent(DiscordUser user, String applicationId, String interactionId, String token, String guildId, String channelId, String commandName, JsonNode options, JsonNode resolved, String timestamp, JsonNode raw) {
        this.user = user;
        this.applicationId = applicationId;
        this.interactionId = interactionId;
        this.token = token;
        this.guildId = guildId;
        this.channelId = channelId;
        this.commandName = commandName;
        this.options = options;
        this.resolved = resolved;
        this.timestamp = timestamp;
        this.raw = raw;
        this.args = new SlashCommandArguments(options, resolved, raw);
    }

    public synchronized String reply(String text) {
        String body = toBody(text);
        if (!responded) {
            responded = true;
            HttpService.postJsonForString(
                    Config.getInstance().getDiscordApiBaseUrl() + "/interactions/" + interactionId + "/" + token + "/callback",
                    "{\"type\":4,\"data\":{\"content\":" + body + "}}"
            );
        } else {
            HttpService.postJsonForString(
                    Config.getInstance().getDiscordApiBaseUrl() + "/webhooks/" + applicationId + "/" + token,
                    "{\"content\":" + body + "}"
            );
        }
        return interactionId;
    }

    public synchronized String followUp(String text) {
        responded = true;
        HttpService.postJsonForString(
                Config.getInstance().getDiscordApiBaseUrl() + "/webhooks/" + applicationId + "/" + token,
                "{\"content\":" + toBody(text) + "}"
        );
        return interactionId;
    }

    private String toBody(String text) {
        try {
            return OBJECT_MAPPER.writeValueAsString(text == null ? "" : text);
        } catch (Exception e) {
            return "\"\"";
        }
    }

}
