package top.yzljc.atricore.platform.qq;

import top.yzljc.atricore.AtriProvider;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atricore.auth.AtriAccount;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.*;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.chat.official.card.Card;
import top.yzljc.atricore.chat.official.management.GroupMember;
import top.yzljc.atricore.chat.official.management.Mute;
import top.yzljc.atricore.platform.*;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * QQ 官方群聊及单聊用户，提供身份查询、权限判断和同步消息回复
 *
 * @Author YZ_Ljc_
 * @ClassName QQUser
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.platform.qq
 */
@Getter
public final class QQUser extends User {
    /**
     * QQ 开放平台绑定的互联 ID，取自用户数据的 {@code union_openid}，未携带时为 {@code null}
     */
    private final String unionOpenId;
    /**
     * QQ 平台提供的用户角色
     */
    private final PlatformRole role;
    /**
     * 事件携带的原始用户数据，未提供时为 {@code null}
     */
    private final JsonNode data;

    /**
     * 创建 QQ 官方群聊或单聊用户
     *
     * @param platform 用户所属平台，仅支持 QQ 官方群聊和单聊
     * @param bot      是否为机器人账号
     * @param userId   用户 OpenID
     * @param username 用户名称
     * @param role     平台角色
     * @param data     原始用户数据，未提供时传入 {@code null}
     * @throws IllegalArgumentException 平台不是 QQ 官方群聊或单聊
     */
    public QQUser(Platform platform, boolean bot, String userId, String username,
                  PlatformRole role, JsonNode data) {
        super(platform, bot, userId, username);
        if (platform != Platform.OFFICIAL_GROUP && platform != Platform.OFFICIAL_C2C) {
            throw new IllegalArgumentException("QQUser 仅支持 QQ 群聊和 C2C");
        }
        this.unionOpenId = data == null ? null : data.path("union_openid").asText(null);
        this.role = role;
        this.data = data;
    }

    /**
     * 获取用户 OpenID，群聊取自 {@code member_openid}，单聊取自 {@code user_openid}
     *
     * @return 用户 OpenID，与 {@link #getUnionOpenId()} 分别保存
     */
    public String getUserOpenId() {
        return userId;
    }

    /**
     * 查询当前用户关联的统一账号
     *
     * @return 统一账号，未关联时返回空的 {@link Optional}
     */
    @Override
    public Optional<AtriAccount> getAccount() {
        return Optional.ofNullable(AtriProvider.getInstance().getAccessService().findByQqUserOpenId(userId));
    }

    /**
     * 判断当前用户是否具有机器人管理权限
     *
     * @return 具有机器人管理权限时返回 {@code true}
     */
    @Override
    public boolean hasPermission() {
        return AtriProvider.getInstance().getAccessService().isAdmin(userId);
    }

    /**
     * 判断当前用户是否具有指定权限，机器人管理员直接通过
     *
     * @param permission 权限节点
     * @return 具有指定权限或机器人管理权限时返回 {@code true}
     */
    @Override
    public boolean hasPermission(String permission) {
        return hasPermission() || AtriProvider.getInstance().getAccessService().hasPermission(userId, permission);
    }

    /**
     * 判断当前用户是否被拉黑或屏蔽
     *
     * @return 被拉黑或屏蔽时返回 {@code true}
     */
    @Override
    public boolean isBlocked() {
        return AtriProvider.getInstance().getAccessService().isBlocked(userId) || AtriProvider.getInstance().getAccessService().isIgnored(userId);
    }

    /**
     * 判断当前用户是否为群管理员或群主
     *
     * @return 平台角色为 {@link PlatformRole#ADMIN} 或 {@link PlatformRole#OWNER} 时返回 {@code true}
     */
    public boolean isPlatformAdmin() {
        return role == PlatformRole.ADMIN || role == PlatformRole.OWNER;
    }

    /**
     * 在指定群中禁言当前用户，需要开放平台与群主授权
     *
     * @param groupId  群 OpenID
     * @param duration 禁言时长，从当前时间起算，{@code null} 表示使用平台默认值
     * @return 禁言成功时返回 {@code true}，请求失败时返回 {@code false}
     * @throws UnsupportedPlatform 当前用户不属于 QQ 官方群聊
     */
    public boolean mute(String groupId, Duration duration) {
        if (this.platform != Platform.OFFICIAL_GROUP) {
            throw new UnsupportedPlatform(this.platform, "mute(String groupId, Duration duration)");
        }
        return Mute.muteMember(groupId, this.userId, duration);
    }

    /**
     * 更新当前用户在指定群中的禁言时长，不新增或解除禁言，需要开放平台与群主授权
     *
     * @param groupId  群 OpenID
     * @param duration 新的禁言时长，从当前时间起算，不可为 {@code null}
     * @return 更新成功时返回 {@code true}，时长为空或请求失败时返回 {@code false}
     * @throws UnsupportedPlatform 当前用户不属于 QQ 官方群聊
     */
    public boolean updateMute(String groupId, Duration duration) {
        if (this.platform != Platform.OFFICIAL_GROUP) {
            throw new UnsupportedPlatform(this.platform, "updateMute(String groupId, Duration duration)");
        }
        return Mute.updateMuteMember(groupId, this.userId, duration);
    }

    /**
     * 解除当前用户在指定群中的禁言，需要开放平台与群主授权
     *
     * @param groupId 群 OpenID
     * @return 解除成功时返回 {@code true}，请求失败时返回 {@code false}
     * @throws UnsupportedPlatform 当前用户不属于 QQ 官方群聊
     */
    public boolean unmute(String groupId) {
        if (this.platform != Platform.OFFICIAL_GROUP) {
            throw new UnsupportedPlatform(this.platform, "unmute(String groupId)");
        }
        return Mute.unmuteMember(groupId, this.userId);
    }

    /**
     * 将当前用户移出指定群，不加入成员黑名单，需要开放平台与群主授权
     *
     * @param groupId 群 OpenID
     * @return 移出成功时返回 {@code true}，请求失败时返回 {@code false}
     * @throws UnsupportedPlatform 当前用户不属于 QQ 官方群聊
     */
    public boolean kick(String groupId) {
        return kick(groupId, false);
    }

    /**
     * 将当前用户移出指定群，并按需加入成员黑名单，需要开放平台与群主授权
     *
     * @param groupId              群 OpenID
     * @param addToMemberBlacklist 是否同时加入该群的成员黑名单
     * @return 移出及所请求的黑名单操作均成功时返回 {@code true}，任一操作失败时返回 {@code false}
     * @throws UnsupportedPlatform 当前用户不属于 QQ 官方群聊
     */
    public boolean kick(String groupId, boolean addToMemberBlacklist) {
        if (this.platform != Platform.OFFICIAL_GROUP) {
            throw new UnsupportedPlatform(this.platform, "kick(String groupId, boolean addToMemberBlacklist)");
        }
        return GroupMember.removeMember(groupId, this.userId, addToMemberBlacklist);
    }

    /**
     * 被动回复 QQ 官方单聊纯文本消息
     *
     * @param messageId 回复来源消息 ID
     * @param text      回复文本
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, String text) {
        if (this.platform == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, RT.message(messageId), text);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String messageId, String text)");
    }

    /**
     * 被动回复 QQ 官方单聊纯文本消息并引用指定消息
     *
     * @param rt     消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param text   回复文本
     * @param refIdx 被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(RT rt, String text, String refIdx) {
        if (this.platform == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, rt, text, refIdx);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(RT rt, String text, String refIdx)");
    }

    /**
     * 被动回复 QQ 官方群聊纯文本消息
     *
     * @param groupId   群 OpenID
     * @param messageId 回复来源消息 ID
     * @param text      回复文本
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, String text) {
        if (this.platform == Platform.OFFICIAL_GROUP) {
            return GroupChat.replyMessage(groupId, RT.message(messageId), text);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, String text)");
    }

    /**
     * 被动回复 QQ 官方群聊纯文本消息并引用指定消息
     *
     * @param groupId   群 OpenID
     * @param messageId 回复来源消息 ID
     * @param text      回复文本
     * @param refIdx    被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, String text, String refIdx) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_GROUP) {
            return GroupChat.replyMessage(groupId, RT.message(messageId), text, refIdx);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, String text, String refIdx)");
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息，默认 @ 当前用户
     *
     * @param groupId   群 OpenID
     * @param messageId 回复来源消息 ID
     * @param md        Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md) {
        return this.sendMessage(groupId, messageId, md, true);
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息，可控制 @ 当前用户
     *
     * @param groupId   群 OpenID
     * @param messageId 回复来源消息 ID
     * @param md        Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param at        是否 @ 当前用户
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md, boolean at) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_GROUP) {
            return at
                    ? GroupChat.replyMessage(groupId, RT.message(messageId), this.userId, md)
                    : GroupChat.replyMessage(groupId, RT.message(messageId), md);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, Markdown md)");
    }

    /**
     * 被动回复 QQ 官方单聊 Markdown 消息
     *
     * @param messageId 回复来源消息 ID
     * @param md        Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, Markdown md) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, RT.message(messageId), md);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String messageId, Markdown md)");
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息，默认 @ 当前用户
     *
     * @param groupId   群 OpenID
     * @param messageId 回复来源消息 ID
     * @param md        Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param keyboard  独立传入的键盘按钮对象，不指定时传入 {@code null}
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     * @deprecated 使用 {@link Markdown#setKeyboard(Keyboard)} 设置按钮，再调用 {@link #sendMessage(String, String, Markdown)}
     */
    @Deprecated
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md, Object keyboard) {
        return this.sendMessage(groupId, messageId, md, keyboard, true);
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息，可控制 @ 当前用户
     *
     * @param groupId   群 OpenID
     * @param messageId 回复来源消息 ID
     * @param md        Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param keyboard  独立传入的键盘按钮对象，不指定时传入 {@code null}
     * @param at        是否 @ 当前用户
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     * @deprecated 使用 {@link Markdown#setKeyboard(Keyboard)} 设置按钮，再调用 {@link #sendMessage(String, String, Markdown, boolean)}
     */
    @Deprecated
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Markdown md, Object keyboard, boolean at) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_GROUP) {
            return at
                    ? GroupChat.replyMessage(groupId, RT.message(messageId), this.userId, md, keyboard)
                    : GroupChat.replyMessage(groupId, RT.message(messageId), md, keyboard);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, Markdown md, Object keyboard)");
    }

    /**
     * 被动回复 QQ 官方单聊 Markdown 消息
     *
     * @param messageId 回复来源消息 ID
     * @param md        Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param keyboard  独立传入的键盘按钮对象，不指定时传入 {@code null}
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     * @deprecated 使用 {@link Markdown#setKeyboard(Keyboard)} 设置按钮，再调用 {@link #sendMessage(String, Markdown)}
     */
    @Deprecated
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, Markdown md, Object keyboard) {
        if (Objects.requireNonNull(this.platform) == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, RT.message(messageId), md, keyboard);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String messageId, Markdown md, Object keyboard)");
    }

    /**
     * 被动回复 QQ 官方单聊卡片消息
     *
     * @param messageId 回复来源消息 ID
     * @param card      卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, Card card) {
        return sendMessage(RT.message(messageId), card);
    }

    /**
     * 被动回复 QQ 官方单聊卡片消息
     *
     * @param rt   消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param card 卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(RT rt, Card card) {
        if (this.platform == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, rt, card);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(RT rt, Card card)");
    }

    /**
     * 被动回复 QQ 官方群聊卡片消息
     *
     * @param groupId   群 OpenID
     * @param messageId 回复来源消息 ID
     * @param card      卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, Card card) {
        return sendMessage(groupId, RT.message(messageId), card);
    }

    /**
     * 被动回复 QQ 官方群聊卡片消息
     *
     * @param groupId 群 OpenID
     * @param rt      消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param card    卡片内容
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, Card card) {
        if (this.platform == Platform.OFFICIAL_GROUP) {
            return GroupChat.replyMessage(groupId, rt, card);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, RT rt, Card card)");
    }

    /**
     * 被动回复 QQ 官方群聊图片消息
     *
     * @param groupId   群 OpenID
     * @param messageId 回复来源消息 ID
     * @param image     图片组件
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, String messageId, ImageComponent image) {
        if (this.platform == Platform.OFFICIAL_GROUP) {
            return GroupChat.replyMessage(groupId, RT.message(messageId), image);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String groupId, String messageId, ImageComponent image)");
    }

    /**
     * 被动回复 QQ 官方单聊图片消息
     *
     * @param messageId 回复来源消息 ID
     * @param image     图片组件
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String messageId, ImageComponent image) {
        if (this.platform == Platform.OFFICIAL_C2C) {
            return C2CChat.replyMessage(this.userId, RT.message(messageId), image);
        }
        throw new UnsupportedPlatform(this.platform, "sendMessage(String messageId, ImageComponent image)");
    }

    /**
     * 被动回复 QQ 官方单聊图片消息并引用指定消息
     *
     * @param rt     消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param image  图片组件
     * @param refIdx 被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
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
     * @param rt       消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param markdown Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param refIdx   被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(RT rt, Markdown markdown, String refIdx) {
        return sendMessage(rt, markdown, null, refIdx);
    }

    /**
     * 被动回复 QQ 官方单聊 Markdown 消息并引用指定消息
     *
     * @param rt       消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param markdown Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param keyboard 独立传入的键盘按钮对象，不指定时传入 {@code null}
     * @param refIdx   被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方单聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     * @deprecated 使用 {@link Markdown#setKeyboard(Keyboard)} 设置按钮，再调用 {@link #sendMessage(RT, Markdown, String)}
     */
    @Deprecated
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
     * @param groupId 群 OpenID
     * @param rt      消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param image   图片组件
     * @param refIdx  被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
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
     * @param groupId  群 OpenID
     * @param rt       消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param markdown Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param refIdx   被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, Markdown markdown, String refIdx) {
        return sendMessage(groupId, rt, markdown, null, true, refIdx);
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息并引用指定消息，可控制 @ 当前用户
     *
     * @param groupId  群 OpenID
     * @param rt       消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param markdown Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param at       是否 @ 当前用户
     * @param refIdx   被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, Markdown markdown, boolean at, String refIdx) {
        return sendMessage(groupId, rt, markdown, null, at, refIdx);
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息并引用指定消息，默认 @ 当前用户
     *
     * @param groupId  群 OpenID
     * @param rt       消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param markdown Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param keyboard 独立传入的键盘按钮对象，不指定时传入 {@code null}
     * @param refIdx   被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     * @deprecated 使用 {@link Markdown#setKeyboard(Keyboard)} 设置按钮，再调用 {@link #sendMessage(String, RT, Markdown, String)}
     */
    @Deprecated
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String groupId, RT rt, Markdown markdown, Object keyboard, String refIdx) {
        return sendMessage(groupId, rt, markdown, keyboard, true, refIdx);
    }

    /**
     * 被动回复 QQ 官方群聊 Markdown 消息并引用指定消息，可控制 @ 当前用户
     *
     * @param groupId  群 OpenID
     * @param rt       消息或事件回复来源，使用 {@link RT#message(String)} 或 {@link RT#event(String)}
     * @param markdown Markdown 内容，可通过 {@link Markdown#setKeyboard(Keyboard)} 设置按钮
     * @param keyboard 独立传入的键盘按钮对象，不指定时传入 {@code null}
     * @param at       是否 @ 当前用户
     * @param refIdx   被引用消息的索引 ID，{@code null} 表示不引用，不改变回复来源
     * @return 发送成功的消息 ID，未取得发送结果时返回 {@code null}
     * @throws UnsupportedPlatform    当前用户不属于 QQ 官方群聊
     * @throws QQMessageSendException QQ 开放平台拒绝发送消息
     * @deprecated 使用 {@link Markdown#setKeyboard(Keyboard)} 设置按钮，再调用 {@link #sendMessage(String, RT, Markdown, boolean, String)}
     */
    @Deprecated
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
