package top.yzljc.atribot.command;

import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.chat.discord.DiscordMessagePayload;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;

import java.nio.file.Path;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName DiscordCommandSender
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public interface DiscordCommandSender extends CommandSender {

    Platform getPlatform();

    boolean isBot();

    String getChannelId();

    PlatformRole getRole();

    String getApplicationId();

    String getInteractionId();

    String getInteractionToken();

    boolean isResponded();

    String sendEphemeralMessage(String text);

    String sendEmbed(DiscordEmbed embed);

    String sendEmbed(String content, DiscordEmbed embed);

    String sendEmbeds(String content, List<DiscordEmbed> embeds);

    String sendEmbeds(String content, List<DiscordEmbed> embeds, boolean ephemeral);

    String sendComponents(String content, Object components);

    String sendEphemeralComponents(String content, Object components);

    String sendFile(Path path);

    String sendFile(Path path, String content);

    String sendFile(Path path, String content, boolean ephemeral);

    String sendPayload(DiscordMessagePayload payload);
}
