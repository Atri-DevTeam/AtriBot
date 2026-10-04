package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.auth.AtriAccount;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.*;
import top.yzljc.atribot.platform.*;

import java.util.Objects;
import java.util.Optional;

/**
 * @Author YZ_Ljc_
 * @ClassName QQUser
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.qq
 */
@Getter
public final class QQUser extends User {
    private final PlatformRole role;
    private final JsonNode data;

    public QQUser(Platform platform, boolean bot, String userId, String username,
                  PlatformRole role, JsonNode data) {
        super(platform, bot, userId, username);
        if (platform != Platform.OFFICIAL_GROUP && platform != Platform.OFFICIAL_C2C) {
            throw new IllegalArgumentException("QQUser 仅支持 QQ 群聊和 C2C");
        }
        this.role = role;
        this.data = data;
    }

    public String getUserOpenId() {
        return userId;
    }

    @Override
    public Optional<AtriAccount> getAccount() {
        return Optional.ofNullable(UnifiedAuthentication.findByQqUserOpenId(userId));
    }

    @Override
    public boolean hasPermission() {
        return OfficialUsers.isAdmin(userId);
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasPermission() || OfficialUsers.hasPermission(userId, permission);
    }

    @Override
    public boolean isBlocked() {
        return OfficialUsers.isBlocked(userId) || OfficialUsers.isIgnored(userId);
    }

    public boolean isPlatformAdmin() {
        return role == PlatformRole.ADMIN || role == PlatformRole.OWNER;
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, String text) {
        switch (this.platform) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyMessage(this.userId, RT.message(messageId), text);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String messageId, String text)");
    }

    /**
     * 被动回复 QQ 官方单聊纯文本消息并引用指定消息
     *
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param text   回复内容
     * @param refIdx 被引用消息的索引 ID，不改变消息或事件回复来源
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(RT rt, String text, String refIdx) {
        if (this.platform == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, rt, text, refIdx);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(RT rt, String text, String refIdx)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, String text) {
        switch (this.platform) {
            case OFFICIAL_GROUP -> {
                return GroupChat.replyMessage(groupId, RT.message(messageId), text);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, String text)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, String text, String refIdx) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_GROUP) {
            return GroupChat.replyMessage(groupId, RT.message(messageId), text, refIdx);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, String text, String refIdx)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md) {
        return this.sendMessage(groupId, messageId, md, true);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md, boolean at) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_GROUP) {
            return at
                    ? GroupChat.replyMessage(groupId, RT.message(messageId), this.userId, md)
                    : GroupChat.replyMessage(groupId, RT.message(messageId), md);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, Markdown md)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, Markdown md) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, RT.message(messageId), md);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String messageId, Markdown md)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md, Object keyboard) {
        return this.sendMessage(groupId, messageId, md, keyboard, true);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md, Object keyboard, boolean at) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_GROUP) {
            return at
                    ? GroupChat.replyMessage(groupId, RT.message(messageId), this.userId, md, keyboard)
                    : GroupChat.replyMessage(groupId, RT.message(messageId), md, keyboard);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, Markdown md, Object keyboard)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, Markdown md, Object keyboard) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, RT.message(messageId), md, keyboard);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String messageId, Markdown md, Object keyboard)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, ImageComponent image) {
        switch (this.platform) {
            case OFFICIAL_GROUP -> {
                return GroupChat.replyMessage(groupId, RT.message(messageId), image);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, ImageComponent image)");
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, ImageComponent image) {
        switch (this.platform) {
            case OFFICIAL_C2C -> {
                return C2CChat.replyMessage(this.userId, RT.message(messageId), image);
            }
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String messageId, ImageComponent image)");
    }

    /**
     * 被动回复 QQ 官方单聊图片消息并引用指定消息
     *
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image  图片组件
     * @param refIdx 被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(RT rt, ImageComponent image, String refIdx) {
        if (this.platform == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, rt, image, refIdx);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(RT rt, ImageComponent image, String refIdx)");
    }

    /**
     * 被动回复 QQ 官方单聊 Markdown 消息并引用指定消息
     *
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(RT rt, Markdown markdown, String refIdx) {
        return sendMessage(rt, markdown, null, refIdx);
    }

    /**
     * 被动回复带键盘的 QQ 官方单聊 Markdown 并引用指定消息
     *
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(RT rt, Markdown markdown, Object keyboard, String refIdx) {
        if (this.platform == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, rt, markdown, keyboard, refIdx);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(RT rt, Markdown markdown, Object keyboard, String refIdx)");
    }

    /**
     * 被动回复 QQ 官方群聊图片消息并引用指定消息
     *
     * @param groupId 群 openId
     * @param rt      消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image   图片组件
     * @param refIdx  被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, ImageComponent image, String refIdx) {
        if (this.platform == Platform.OFFICIAL_GROUP) {
            return GroupChat.replyMessage(groupId, rt, image, refIdx);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, RT rt, ImageComponent image, String refIdx)");
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息并引用指定消息，默认 @ 当前用户
     *
     * @param groupId  群 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, Markdown markdown, String refIdx) {
        return sendMessage(groupId, rt, markdown, null, true, refIdx);
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息并引用指定消息，可控制 @
     *
     * @param groupId  群 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param at       是否 @ 当前用户
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, Markdown markdown, boolean at, String refIdx) {
        return sendMessage(groupId, rt, markdown, null, at, refIdx);
    }

    /**
     * 被动回复带键盘的 QQ 官方群聊 Markdown 并引用指定消息，默认 @ 当前用户
     *
     * @param groupId  群 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, Markdown markdown, Object keyboard, String refIdx) {
        return sendMessage(groupId, rt, markdown, keyboard, true, refIdx);
    }

    /**
     * 被动回复带键盘的 QQ 官方群聊 Markdown 并引用指定消息
     *
     * @param groupId  群 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @param at       是否 @ 当前用户
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, Markdown markdown, Object keyboard, boolean at, String refIdx) {
        if (this.platform == Platform.OFFICIAL_GROUP) {
            return at
                    ? GroupChat.replyMessage(groupId, rt, this.userId, markdown, keyboard, refIdx)
                    : GroupChat.replyMessage(groupId, rt, markdown, keyboard, refIdx);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, RT rt, Markdown markdown, Object keyboard, boolean at, String refIdx)");
    }

}
