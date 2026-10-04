package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.auth.AtriAccount;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Embed;
import top.yzljc.atribot.chat.official.GuildChannelChat;
import top.yzljc.atribot.chat.official.GuildDirectChat;
import top.yzljc.atribot.chat.official.RT;
import top.yzljc.atribot.platform.*;

import java.util.Optional;

/**
 * @Author YZ_Ljc_
 * @ClassName QQGuildUser
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.qq
 */
@Getter
public final class QQGuildUser extends User {
    private final String userOpenId;
    private final PlatformRole role;
    private final JsonNode data;

    public QQGuildUser(Platform platform, boolean bot, String channelUserId, String username,
                       String userOpenId, PlatformRole role, JsonNode data) {
        super(platform, bot, channelUserId, username);
        if (platform != Platform.OFFICIAL_GUILD_CHANNEL && platform != Platform.OFFICIAL_GUILD_DM) {
            throw new IllegalArgumentException("QQGuildUser 仅支持 QQ 频道和频道私信");
        }
        this.userOpenId = userOpenId;
        this.role = role;
        this.data = data;
    }

    public String getChannelUserId() {
        return userId;
    }

    @Override
    public Optional<AtriAccount> getAccount() {
        return Optional.ofNullable(UnifiedAuthentication.findByQqUserOpenId(userOpenId));
    }

    @Override
    public boolean hasPermission() {
        return userOpenId != null && !userOpenId.isBlank() && OfficialUsers.isAdmin(userOpenId);
    }

    @Override
    public boolean hasPermission(String permission) {
        return userOpenId != null && !userOpenId.isBlank()
                && (hasPermission() || OfficialUsers.hasPermission(userOpenId, permission));
    }

    @Override
    public boolean isBlocked() {
        return false;
    }

    public boolean isPlatformAdmin() {
        return role == PlatformRole.ADMIN || role == PlatformRole.OWNER;
    }

    /**
     * 被动回复 QQ 频道或频道私信中的文本。
     *
     * @param targetId 文字子频道的 {@code channelId}，或私信会话的 {@code guildId}
     * @param messageId 用于被动回复的来源消息 ID
     * @param text 回复内容
     * @return 消息 ID，发送失败返回 {@code null}
     */
    public String sendMessage(String targetId, String messageId, String text) {
        return platform == Platform.OFFICIAL_GUILD_CHANNEL
                ? GuildChannelChat.replyMessage(targetId, RT.message(messageId), text)
                : GuildDirectChat.replyMessage(targetId, RT.message(messageId), text);
    }

    /**
     * 被动回复 QQ 频道或频道私信中的图片。
     *
     * @param targetId 文字子频道的 {@code channelId}，或私信会话的 {@code guildId}
     * @param messageId 用于被动回复的来源消息 ID
     * @param image 回复内容
     * @return 消息 ID，发送失败返回 {@code null}
     */
    public String sendMessage(String targetId, String messageId, ImageComponent image) {
        return platform == Platform.OFFICIAL_GUILD_CHANNEL
                ? GuildChannelChat.replyMessage(targetId, RT.message(messageId), image)
                : GuildDirectChat.replyMessage(targetId, RT.message(messageId), image);
    }

    /**
     * 被动回复 QQ 频道或频道私信中的嵌入消息。
     *
     * @param targetId 文字子频道的 {@code channelId}，或私信会话的 {@code guildId}
     * @param messageId 用于被动回复的来源消息 ID
     * @param embed 回复内容
     * @return 消息 ID，发送失败返回 {@code null}
     */
    public String sendMessage(String targetId, String messageId, Embed embed) {
        return platform == Platform.OFFICIAL_GUILD_CHANNEL
                ? GuildChannelChat.replyMessage(targetId, RT.message(messageId), embed)
                : GuildDirectChat.replyMessage(targetId, RT.message(messageId), embed);
    }
}
