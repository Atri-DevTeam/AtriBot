package top.yzljc.atribot.command;

import lombok.Getter;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventType;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.chat.discord.DiscordMessagePayload;
import top.yzljc.atribot.platform.discord.DiscordUser;
import top.yzljc.atribot.service.request.HttpService;

import java.nio.file.Path;
import java.util.List;

@Getter
public class DiscordSlashCommandSender extends CommandSender {

    private final String applicationId;
    private final String interactionId;
    private final String interactionToken;
    private final String channelId;
    private boolean responded;

    public DiscordSlashCommandSender(DiscordUser user, String applicationId, String interactionId, String interactionToken) {
        super(
                user.getGuildId() != null ? Platform.DISCORD_GUILD : Platform.DISCORD_DM,
                user.isBot(),
                user.getUserId(),
                user.getUsername(),
                user.getGuildId(),
                interactionId,
                user.getData(),
                List.<User>of(),
                user.getRole() == null ? PlatformRole.MEMBER : user.getRole(),
                EventType.DISCORD_SLASH_COMMAND
        );
        this.applicationId = applicationId;
        this.interactionId = interactionId;
        this.interactionToken = interactionToken;
        this.channelId = user.getChannelId();
    }

    @Override
    public String sendMessage(String text) {
        return sendPayload(DiscordMessagePayload.text(text));
    }

    public String sendEphemeralMessage(String text) {
        return sendPayload(DiscordMessagePayload.text(text).ephemeral());
    }

    public String sendEmbed(DiscordEmbed embed) {
        return sendPayload(DiscordMessagePayload.embed(embed));
    }

    public String sendEmbed(String content, DiscordEmbed embed) {
        return sendPayload(new DiscordMessagePayload().content(content).addEmbed(embed));
    }

    public String sendEmbeds(String content, List<DiscordEmbed> embeds) {
        return sendEmbeds(content, embeds, false);
    }

    public String sendEmbeds(String content, List<DiscordEmbed> embeds, boolean ephemeral) {
        DiscordMessagePayload payload = new DiscordMessagePayload().content(content);
        if (embeds != null) {
            for (DiscordEmbed embed : embeds) {
                payload.addEmbed(embed);
            }
        }
        return sendPayload(payload.ephemeral(ephemeral));
    }

    public String sendComponents(String content, Object components) {
        return sendPayload(new DiscordMessagePayload().content(content).components(components));
    }

    public String sendEphemeralComponents(String content, Object components) {
        return sendPayload(new DiscordMessagePayload().content(content).components(components).ephemeral());
    }

    public String sendFile(Path path) {
        return sendPayload(new DiscordMessagePayload().file(path));
    }

    public String sendFile(Path path, String content) {
        return sendPayload(new DiscordMessagePayload().content(content).file(path));
    }

    public String sendFile(Path path, String content, boolean ephemeral) {
        return sendPayload(new DiscordMessagePayload().content(content).file(path).ephemeral(ephemeral));
    }

    public synchronized String sendPayload(DiscordMessagePayload payload) {
        if (payload == null) {
            payload = new DiscordMessagePayload();
        }

        if (!responded) {
            responded = true;
            String url = Config.getInstance().getDiscordApiBaseUrl() + "/interactions/" + interactionId + "/" + interactionToken + "/callback";
            if (payload.hasFiles()) {
                HttpService.postMultipartForString(url, payload.toInteractionMultipartFields(), payload.toMultipartFiles());
            } else {
                HttpService.postJsonForString(url, payload.toInteractionResponseJson());
            }
        } else {
            String url = Config.getInstance().getDiscordApiBaseUrl() + "/webhooks/" + applicationId + "/" + interactionToken;
            if (payload.hasFiles()) {
                HttpService.postMultipartForString(url, payload.toWebhookMultipartFields(), payload.toMultipartFiles());
            } else {
                HttpService.postJsonForString(url, payload.toWebhookJson());
            }
        }
        return interactionId;
    }
}
