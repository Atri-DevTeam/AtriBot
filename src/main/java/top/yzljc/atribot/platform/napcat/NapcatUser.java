package top.yzljc.atribot.platform.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.auth.AtriAccount;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.PrivateMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.*;

import java.util.Optional;

/**
 * @Author YZ_Ljc_
 * @ClassName NapcatUser
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.napcat
 */
@Getter
public final class NapcatUser extends User {
    private final PlatformRole role;
    private final JsonNode data;

    public NapcatUser(Platform platform, boolean bot, String userId, String username,
                      PlatformRole role, JsonNode data) {
        super(platform, bot, userId, username);
        if (platform != Platform.NAPCAT_GROUP && platform != Platform.NAPCAT_PRIVATE) {
            throw new IllegalArgumentException("NapcatUser 仅支持 Napcat 群聊和私聊");
        }
        this.role = role;
        this.data = data;
    }

    @Override
    public Optional<AtriAccount> getAccount() {
        return Optional.ofNullable(UnifiedAuthentication.findByQqUserUin(userId));
    }

    @Override
    public boolean hasPermission() {
        return (platform == Platform.NAPCAT_GROUP && Config.getInstance().getNapcatAdminUins().contains(userId))
                || OfficialUsers.isAdmin(userId);
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

    public String sendMessage(String messageId, String text) {
        if (platform != Platform.NAPCAT_PRIVATE) throw new UnsupportedPlatform(platform, "sendMessage(messageId, text)");
        return PrivateMessage.replyMessage(userId, messageId, text);
    }

    public String sendMessage(String groupId, String messageId, String text) {
        if (platform != Platform.NAPCAT_GROUP) throw new UnsupportedPlatform(platform, "sendMessage(groupId, messageId, text)");
        return GroupMessage.replyMessage(userId, groupId, messageId, false, text);
    }

    public String sendMessage(String messageId, ImageComponent image) {
        if (platform != Platform.NAPCAT_PRIVATE) throw new UnsupportedPlatform(platform, "sendMessage(messageId, image)");
        return PrivateMessage.replyMessage(userId, messageId, image);
    }

    public String sendMessage(String groupId, String messageId, ImageComponent image) {
        if (platform != Platform.NAPCAT_GROUP) throw new UnsupportedPlatform(platform, "sendMessage(groupId, messageId, image)");
        return GroupMessage.replyMessage(groupId, messageId, image);
    }
}
