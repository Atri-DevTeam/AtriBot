package top.yzljc.atribot.command;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.event.EventType;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.UnsupportedPlatform;
import top.yzljc.atribot.platform.User;

import java.util.ArrayList;
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
    private final JsonNode attachments;

    public CommandSender(Platform platform, boolean bot, String userId, String username, String groupId, String messageId,
                         JsonNode data, List<User> mentions, PlatformRole role, EventType eventType) {
        this(platform, bot, userId, username, groupId, messageId, data, mentions, role, eventType, null);
    }

    public CommandSender(Platform platform, boolean bot, String userId, String username, String groupId, String messageId,
                         JsonNode data, List<User> mentions, PlatformRole role, EventType eventType, JsonNode attachments) {
        super(platform, bot, userId, username, role, data);
        this.groupId = groupId;
        this.messageId = messageId;
        this.mentions = mentions;
        this.eventType = eventType;
        this.attachments = attachments;
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

    /**
     * 触发本次指令的消息所携带的附件数组，来源于事件原始报文的 {@code attachments} 字段。
     * 消息不含附件时为 null。
     */
    public JsonNode getAttachments() {
        return this.attachments;
    }

    /**
     * 从附件中筛出图片直链
     *
     * <p>官方 Bot 的图片附件形如
     * {@code {"content_type":"image/png","filename":"...","url":"multimedia.nt.qq.com.cn/download?...","width":765,"height":160,"size":27783}}，
     * 其中 {@code url} 不带协议头且带有会过期的 rkey，这里统一补全为 https 链接。
     *
     * @return 按附件顺序排列的图片直链，无图片时返回空列表
     */
    public List<String> getImageUrls() {
        if (this.attachments == null || !this.attachments.isArray()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (JsonNode attachment : this.attachments) {
            String contentType = attachment.path("content_type").asText("");
            if (!contentType.startsWith("image/")) continue;
            String url = attachment.path("url").asText(null);
            if (url == null || url.isBlank()) continue;
            urls.add(url.startsWith("http") ? url : "https://" + url);
        }
        return urls;
    }

    /**
     * 取第一张图片附件的原始节点，便于读取 filename / size / 宽高等元信息。
     */
    public JsonNode getFirstImageAttachment() {
        if (this.attachments == null || !this.attachments.isArray()) {
            return null;
        }
        for (JsonNode attachment : this.attachments) {
            if (attachment.path("content_type").asText("").startsWith("image/")) {
                return attachment;
            }
        }
        return null;
    }

    /**
     * QQ官机频道中，{@code groupId}字段请传入所在文字子频道的{@code channelId}，频道私信中，{@code grouId}字段请传入所在频道的{@code guildId}
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text) {
        switch (platform) {
            case OFFICIAL_GROUP, NAPCAT_GROUP, OFFICIAL_GUILD_CHANNEL, OFFICIAL_GUILD_DM -> {
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
    public String sendMessage(Markdown markdown, boolean at) {
        switch (platform) {
            case OFFICIAL_GROUP -> {
                return super.sendMessage(this.groupId, this.messageId, markdown, at);
            }
            case OFFICIAL_C2C -> {
                return super.sendMessage(this.messageId, markdown);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(Markdown markdown, boolean at)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown markdown, Object buttons, boolean at) {
        switch (platform) {
            case OFFICIAL_GROUP -> {
                return super.sendMessage(this.groupId, this.messageId, markdown, buttons, at);
            }
            case OFFICIAL_C2C -> {
                return super.sendMessage(this.messageId, markdown, buttons);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(Markdown markdown, Object buttons, boolean at)");
    }

    /**
     * QQ官机频道 - 文字子频道中，{@code groupId}字段请传入所在文字子频道的{@code channelId}<br>
     *
     * QQ官机频道 - 频道私信中，{@code groupId}字段请传入所在频道的{@code guildId}
     * 且 {@param type} 只能为 {@link ImageType#URL}
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String data, ImageType type) {
        switch (platform) {
            case OFFICIAL_GROUP, OFFICIAL_GUILD_CHANNEL, OFFICIAL_GUILD_DM -> {
                return super.sendMessage(this.groupId, this.messageId, data, type);
            }
            case OFFICIAL_C2C -> {
                return super.sendMessage(this.messageId, data, type);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String data, ImageType type)");
    }

    /**
     * QQ官机频道 - 文字子频道中，{@code groupId}字段请传入所在文字子频道的{@code channelId}<br>
     *
     * QQ官机频道 - 频道私信中，{@code groupId}字段请传入所在频道的{@code guildId}
     * 且 {@param type} 只能为 {@link ImageType#URL}
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text, String data, ImageType type) {
        switch (platform) {
            case OFFICIAL_GROUP, OFFICIAL_GUILD_CHANNEL, OFFICIAL_GUILD_DM -> {
                return super.sendMessage(this.groupId, this.messageId, text, data, type);
            }
            case OFFICIAL_C2C -> {
                return C2CChat.replyMessage(this.userId, this.messageId, text, type, data);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String text, String data, ImageType type)");
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
    public String sendStreamTextMessage(List<String> textDeltas) {
        switch (platform) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyTextStreamDeltas(userId, messageId, textDeltas);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(List<String> textDeltas)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendStreamMarkdownMessage(List<Markdown> textDeltas) {
        switch (platform) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyStreamDeltas(userId, messageId, textDeltas);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(List<String> textDeltas)");
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