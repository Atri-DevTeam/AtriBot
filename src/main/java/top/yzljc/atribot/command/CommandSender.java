package top.yzljc.atribot.command;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.event.EventType;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.UnsupportedPlatform;
import top.yzljc.atribot.platform.User;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandSender
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public class CommandSender extends User {
    private final String groupId;
    private final String messageId;
    private final List<User> mentions;
    private final EventType eventType;

    public CommandSender(Platform platform, boolean bot, String userId, String username, String groupId, String messageId,
                         JsonNode data, List<User> mentions, PlatformRole role, EventType eventType) {
        super(platform, bot, userId, username, role, data);
        this.groupId = groupId;
        this.messageId = messageId;
        this.mentions = mentions;
        this.eventType = eventType;
    }

    public String getGroupId() {
        return this.groupId;
    }

    public String getMessageId() {
        return this.messageId;
    }

    public List<User> getMentions() {
        return this.mentions;
    }

    public EventType getEventType() {
        return this.eventType;
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text) {
        switch (platform) {
            case OFFICIAL_GROUP, NAPCAT_GROUP -> {
                return super.sendMessage(this.groupId, this.messageId, text);
            }
            case OFFICIAL_C2C -> {
                return super.sendMessage(this.messageId, text);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String text)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown markdown) {
        switch (platform) {
            case OFFICIAL_GROUP -> {
                return super.sendMessage(this.groupId, this.messageId, markdown);
            }
            case OFFICIAL_C2C -> {
                return super.sendMessage(this.messageId, markdown);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(Markdown markdown)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown markdown, Object buttons) {
        switch (platform) {
            case OFFICIAL_GROUP -> {
                return super.sendMessage(this.groupId, this.messageId, markdown, buttons);
            }
            case OFFICIAL_C2C -> {
                return super.sendMessage(this.messageId, markdown, buttons);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(Markdown markdown, Object buttons)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String data, ImageType type) {
        switch (platform) {
            case OFFICIAL_GROUP -> {
                return super.sendMessage(this.groupId, this.messageId, data, type);
            }
            case OFFICIAL_C2C -> {
                return super.sendMessage(this.messageId, data, type);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String data, ImageType type)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String data, MessageUtils.ImageType type) {
        switch (platform) {
            case NAPCAT_GROUP, NAPCAT_PRIVATE -> {
                return super.sendMessage(this.groupId, this.messageId, data, type);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String data, MessageUtils.ImageType type)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text, String data, MessageUtils.ImageType type) {
        switch (platform) {
            case NAPCAT_GROUP, NAPCAT_PRIVATE -> {
                return super.sendMessage(this.groupId, this.messageId, text, data, type);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String text, String data, MessageUtils.ImageType type)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public void recall(String messageId) {
        switch (platform) {
            case OFFICIAL_GROUP -> super.recall(this.groupId, messageId);
            case OFFICIAL_C2C -> super.recall(messageId);
            case NAPCAT_GROUP -> super.recall(this.groupId, messageId);
            default -> throw new UnsupportedPlatform(this.platform, "recall(String messageId)");
        }
    }
}