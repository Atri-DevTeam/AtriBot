package top.yzljc.atribot.command;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.User;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandSender
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
@Getter
public class CommandSender extends User {
    private final String groupId;
    private final String messageId;
    private final List<User> mentions;

    public CommandSender(Platform platform, boolean bot, String userId, String username, String groupId, String messageId, JsonNode data, List<User> mentions, PlatformRole role) {
        super(platform, bot, userId, username, role, data);
        this.groupId = groupId;
        this.messageId = messageId;
        this.mentions = mentions;
    }

    public boolean hasPermission() {
        if (this.platform == Platform.NAPCAT_GROUP) {
            if (Config.getInstance().getNapcatAdminUins().contains(this.userId)) {
                return true;
            }
        }
        return OfficialUsers.isAdmin(this.userId);
    }

    public boolean hasPermission(String permission) {
        if (this.platform == Platform.NAPCAT_GROUP) {
            if (Config.getInstance().getNapcatAdminUins().contains(this.userId)) {
                return true;
            }
        }
        if (OfficialUsers.isAdmin(this.userId)) return true;
        return OfficialUsers.hasPermission(this.userId, permission);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text) {
        switch (platform) {
            case OFFICIAL_GROUP, NAPCAT_GROUP -> {
                return sendMessage(this.groupId, this.messageId, text);
            }
            case OFFICIAL_C2C -> {
                return sendMessage(this.messageId, text);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown markdown) {
        switch (platform) {
            case OFFICIAL_GROUP -> {
                return sendMessage(this.groupId, this.messageId, markdown);
            }
            case OFFICIAL_C2C -> {
                return sendMessage(this.messageId, markdown);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown markdown, Object buttons) {
        switch (platform) {
            case OFFICIAL_GROUP -> {
                return sendMessage(this.groupId, this.messageId, markdown, buttons);
            }
            case OFFICIAL_C2C -> {
                return sendMessage(this.messageId, markdown, buttons);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String data, ImageType type) {
        switch (platform) {
            case OFFICIAL_GROUP -> {
                return sendMessage(this.groupId, this.messageId, data, type);
            }
            case OFFICIAL_C2C -> {
                return sendMessage(this.messageId, data, type);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String data, MessageUtils.ImageType type) {
        switch (platform) {
            case NAPCAT_GROUP, NAPCAT_C2C -> {
                return sendMessage(this.groupId, this.messageId, data, type);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text, String data, MessageUtils.ImageType type) {
        switch (platform) {
            case NAPCAT_GROUP, NAPCAT_C2C -> {
                return sendMessage(this.groupId, this.messageId, text, data, type);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public void recall(String messageId) {
        switch (platform) {
            case OFFICIAL_GROUP -> recall(this.groupId, messageId);
            case OFFICIAL_C2C -> recall(messageId);
            case NAPCAT_GROUP -> recall(this.groupId, messageId);
            default -> throw new UnsupportedOperationException("Unsupported platform: " + platform);
        }
    }
}