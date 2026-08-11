package top.yzljc.atribot.command.impl;

import lombok.RequiredArgsConstructor;
import top.yzljc.atribot.command.DiscordCommandSender;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.chat.discord.DiscordMessagePayload;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.discord.DiscordUser;
import top.yzljc.atribot.service.request.HttpService;

import java.nio.file.Path;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName DiscordSenderImpl
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.botcommand.impl
 */
@RequiredArgsConstructor
public class DiscordSenderImpl implements DiscordCommandSender {

    private final DiscordUser user;
    private final String applicationId;
    private final String interactionId;
    private final String interactionToken;
    private boolean responded;

    @Override
    public Platform getPlatform() {
        return this.user.getPlatform();
    }

    @Override
    public boolean isBot() {
        return this.user.isBot();
    }

    @Override
    public String getChannelId() {
        return this.user.getChannelId();
    }

    @Override
    public PlatformRole getRole() {
        return this.user.getRole();
    }

    @Override
    public String getApplicationId() {
        return this.applicationId;
    }

    @Override
    public String getInteractionId() {
        return this.interactionId;
    }

    @Override
    public String getInteractionToken() {
        return this.interactionToken;
    }

    @Override
    public boolean isResponded() {
        return this.responded;
    }

    @Override
    public String getUserId() {
        return this.user.getUserId();
    }

    @Override
    public String getUsername() {
        return this.user.getUsername();
    }

    @Override
    public boolean hasPermission() {
        return this.user.hasPermission();
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.user.hasPermission(permission);
    }

    @Override
    public String sendMessage(String text) {
        return sendPayload(DiscordMessagePayload.text(text));
    }

    @Override
    public String sendEphemeralMessage(String text) {
        return sendPayload(DiscordMessagePayload.text(text).ephemeral());
    }

    @Override
    public String sendEmbed(DiscordEmbed embed) {
        return sendPayload(DiscordMessagePayload.embed(embed));
    }

    @Override
    public String sendEmbed(String content, DiscordEmbed embed) {
        return sendPayload(new DiscordMessagePayload().content(content).addEmbed(embed));
    }

    @Override
    public String sendEmbeds(String content, List<DiscordEmbed> embeds) {
        return sendEmbeds(content, embeds, false);
    }

    @Override
    public String sendEmbeds(String content, List<DiscordEmbed> embeds, boolean ephemeral) {
        DiscordMessagePayload payload = new DiscordMessagePayload().content(content);
        if (embeds != null) {
            for (DiscordEmbed embed : embeds) {
                payload.addEmbed(embed);
            }
        }
        return sendPayload(payload.ephemeral(ephemeral));
    }

    @Override
    public String sendComponents(String content, Object components) {
        return sendPayload(new DiscordMessagePayload().content(content).components(components));
    }

    @Override
    public String sendEphemeralComponents(String content, Object components) {
        return sendPayload(new DiscordMessagePayload().content(content).components(components).ephemeral());
    }

    @Override
    public String sendFile(Path path) {
        return sendPayload(new DiscordMessagePayload().file(path));
    }

    @Override
    public String sendFile(Path path, String content) {
        return sendPayload(new DiscordMessagePayload().content(content).file(path));
    }

    @Override
    public String sendFile(Path path, String content, boolean ephemeral) {
        return sendPayload(new DiscordMessagePayload().content(content).file(path).ephemeral(ephemeral));
    }

    @Override
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
