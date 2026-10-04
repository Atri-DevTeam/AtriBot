package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.User;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName QQGuildMessage
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.qq
 */
@Getter
public final class QQGuildMessage extends Message {
    private final String guildId;
    private final String channelId;
    private final JsonNode attachments;
    private final JsonNode embeds;
    private final JsonNode ark;
    private final JsonNode reference;
    private final JsonNode raw;

    public QQGuildMessage(Platform platform, String messageId, String content, String timestamp,
                          List<User> mentionedUsers, String guildId, String channelId, JsonNode raw) {
        super(platform, messageId, content, timestamp, mentionedUsers);
        if (platform != Platform.OFFICIAL_GUILD_CHANNEL && platform != Platform.OFFICIAL_GUILD_DM) {
            throw new IllegalArgumentException("QQGuildMessage 仅支持 QQ 频道和频道私信");
        }
        this.guildId = guildId;
        this.channelId = channelId;
        this.raw = raw;
        this.attachments = raw == null ? null : raw.get("attachments");
        this.embeds = raw == null ? null : raw.get("embeds");
        this.ark = raw == null ? null : raw.get("ark");
        this.reference = raw == null ? null : raw.get("message_reference");
    }
}
