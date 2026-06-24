package top.yzljc.atribot.platform;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.PrivateMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.media.ImageType;

/**
 * @Author YZ_Ljc_
 * @ClassName User
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform
 */
public class User {
    protected final Platform platform;
    protected final boolean bot;
    protected final String userId;
    protected final String username;
    protected final PlatformRole role;
    protected final JsonNode data;

    public User(Platform platform, boolean bot, String userId, String username, PlatformRole role, JsonNode data) {
        this.platform = platform;
        this.bot = bot;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.data = data;
    }

    public Platform getPlatform() {
        return platform;
    }

    public boolean isBot() {
        return bot;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public PlatformRole getRole() {
        return role;
    }

    public JsonNode getData() {
        return data;
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, String text) {
        switch (this.platform) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyMessage(this.userId, messageId, text);
            }
            case NAPCAT_PRIVATE -> {
                return PrivateMessage.replyMessage(this.userId, messageId, text);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, String text) {
        switch (this.platform) {
            case OFFICIAL_GROUP -> {
                return GroupChat.replyMessage(groupId, messageId, text);
            }
            case NAPCAT_GROUP -> {
                return GroupMessage.replyMessage(this.userId, groupId, messageId, false, text);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md) {
        switch (this.platform) {
            case OFFICIAL_GROUP -> {
                return GroupChat.replyMessage(groupId, this.userId, messageId, md);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, Markdown md) {
        switch (this.platform) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyMessage(this.userId, messageId, md);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md, Object keyboard) {
        switch (this.platform) {
            case OFFICIAL_GROUP -> {
                return GroupChat.replyMessage(groupId, this.userId, messageId, md, keyboard);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, Markdown md, Object keyboard) {
        switch (this.platform) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyMessage(this.userId, messageId, md, keyboard);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, String data, ImageType type) {
        switch (this.platform) {
            case OFFICIAL_GROUP -> {
                return GroupChat.replyMessage(groupId, messageId, type, data);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, String data, ImageType type) {
        switch (this.platform) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyMessage(this.userId, messageId, type, data);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, String text, String data, MessageUtils.ImageType type) {
        switch (this.platform) {
            case NAPCAT_GROUP -> {
                return GroupMessage.replyMessage(groupId, messageId, text, data, type);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, String data, MessageUtils.ImageType type) {
        switch (this.platform) {
            case NAPCAT_GROUP -> {
                return GroupMessage.replyMessage(groupId, messageId, data, type);
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public void recall(String groupId, String messageId) {
        switch (this.platform) {
            case OFFICIAL_GROUP -> {
                GroupChat.recallMessage(groupId, messageId);
                return;
            }
            case NAPCAT_GROUP -> {
                GroupMessage.recallMessage(messageId);
                return;
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }

    @SuppressWarnings("UnusedReturnValue")
    public void recall(String messageId) {
        switch (this.platform) {
            case OFFICIAL_C2C -> {
                C2CChat.recallMessage(this.userId, messageId);
                return;
            }
            case NAPCAT_PRIVATE -> {
                PrivateMessage.recallMessage(messageId);
                return;
            }
        }
        throw new UnsupportedOperationException("Unsupported platform: " + this.platform);
    }
}