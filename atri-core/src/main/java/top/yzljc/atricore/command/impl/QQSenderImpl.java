package top.yzljc.atricore.command.impl;

import top.yzljc.atricore.chat.official.button.ActionButton;

import lombok.AllArgsConstructor;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.card.Card;
import top.yzljc.atricore.event.EventType;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.platform.PlatformRole;
import top.yzljc.atricore.platform.UnsupportedPlatform;
import top.yzljc.atricore.platform.qq.QQUser;
import top.yzljc.atricore.platform.qq.QQMessage;

import java.util.List;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName QQSenderImpl
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command.impl
 */
@AllArgsConstructor
public class QQSenderImpl implements QQCommandSender {

    private final QQUser user;
    private final String groupId;
    private final QQMessage message;

    @Override
    public Platform getPlatform() {
        return this.user.getPlatform();
    }

    @Override
    public boolean isBot() {
        return this.user.isBot();
    }

    @Override
    public String getGroupId() {
        return this.groupId;
    }

    @Override
    public PlatformRole getRole() {
        return this.user.getRole();
    }

    @Override
    public QQMessage getMessage() {
        return this.message;
    }

    @Override
    public String getUserId() {
        return this.user.getUserId();
    }

    @Override
    public String getUsername() {
        return this.user.getUsername();
    }

    @Override
    public boolean hasPermission() {
        return this.user.hasPermission();
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.user.hasPermission(permission);
    }

    @Override
    public String sendMessage(String text) {
        return sendMessage(text, null);
    }

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param text         文本内容
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    @Override
    public String sendMessage(String text, ActionButton actionButton) {
        if (this.user.getPlatform().equals(Platform.OFFICIAL_GROUP)) {
            if (this.message.getMessageEventType().equals(EventType.OFFICIAL_GROUP_MESSAGE)) {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), text, this.message.getRefIdx());
            } else {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), text);
            }
        } else {
            return this.user.sendMessage(this.message.getMessageId(), text, actionButton);
        }
    }

    @Override
    public String sendMessage(String text, boolean ref) {
        return sendMessage(text, ref, null);
    }

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param text         文本内容
     * @param ref          是否引用当前消息
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    @Override
    public String sendMessage(String text, boolean ref, ActionButton actionButton) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return ref
                        ? this.user.sendMessage(this.groupId, this.message.getMessageId(), text, this.message.getRefIdx())
                        : this.user.sendMessage(this.groupId, this.message.getMessageId(), text);
            }
            case OFFICIAL_C2C -> {
                return ref
                        ? this.user.sendMessage(RT.message(this.message.getMessageId()), text, this.message.getRefIdx(), actionButton)
                        : this.user.sendMessage(this.message.getMessageId(), text, actionButton);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(String text, boolean ref)");
    }

    @Override
    public String sendMessage(Markdown markdown) {
        return sendMessage(markdown, null);
    }

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param markdown     Markdown 内容，可携带键盘
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    @Override
    public String sendMessage(Markdown markdown, ActionButton actionButton) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), markdown, actionButton);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(Markdown markdown)");
    }

    @Override
    public String sendMessage(Markdown markdown, boolean at) {
        return sendMessage(markdown, at, null);
    }

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param markdown     Markdown 内容，可携带键盘
     * @param at           是否自动 @ 用户，仅群聊生效
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    @Override
    public String sendMessage(Markdown markdown, boolean at, ActionButton actionButton) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return this.user.sendMessage(this.groupId, this.message.getMessageId(), markdown, at);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), markdown, actionButton);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(Markdown markdown, boolean at)");
    }

    @Override
    public String sendMessage(ImageComponent image) {
        return sendMessage(image, null);
    }

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param image        图片组件
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    @Override
    public String sendMessage(ImageComponent image, ActionButton actionButton) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {

                if (this.message.getMessageEventType() == EventType.OFFICIAL_GROUP_MESSAGE) {
                    RT rt = RT.message(this.message.getMessageId());
                    return this.user.sendMessage(this.groupId, rt, image, this.message.getRefIdx());
                }

                return this.user.sendMessage(this.groupId, this.message.getMessageId(), image);
            }
            case OFFICIAL_C2C -> {
                return this.user.sendMessage(this.message.getMessageId(), image, actionButton);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(ImageComponent image)");
    }

    @Override
    public String sendMessage(Card card) {
        return switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> this.user.sendMessage(this.groupId, this.message.getMessageId(), card);
            case OFFICIAL_C2C -> this.user.sendMessage(this.message.getMessageId(), card);
            default -> throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(Card card)");
        };
    }

    @Override
    public String sendMessage(ImageComponent image, boolean ref) {
        return sendMessage(image, ref, null);
    }

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param image        图片组件
     * @param ref          是否引用当前消息
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    @Override
    public String sendMessage(ImageComponent image, boolean ref, ActionButton actionButton) {
        if (!ref) {
            return sendMessage(image, actionButton);
        }
        RT rt = RT.message(this.message.getMessageId());
        return switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> this.user.sendMessage(this.groupId, rt, image, this.message.getRefIdx());
            case OFFICIAL_C2C -> this.user.sendMessage(rt, image, this.message.getRefIdx(), actionButton);
            default ->
                    throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(ImageComponent image, boolean ref)");
        };
    }

    @Override
    public String sendMessage(Markdown markdown, boolean at, boolean ref) {
        return sendMessage(markdown, at, ref, null);
    }

    /**
     * 发送消息并附带操作栏，沿用原发送入口的回复与引用规则
     *
     * @param markdown     Markdown 内容，可携带键盘
     * @param at           是否自动 @ 用户，仅群聊生效
     * @param ref          是否引用当前消息
     * @param actionButton 私聊消息操作栏，群聊忽略此参数，null 表示不额外设置
     * @return 平台返回的消息 ID
     */
    @Override
    public String sendMessage(Markdown markdown, boolean at, boolean ref, ActionButton actionButton) {
        if (!ref) {
            return sendMessage(markdown, at, actionButton);
        }
        RT rt = RT.message(this.message.getMessageId());
        return switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> this.user.sendMessage(this.groupId, rt, markdown, at, this.message.getRefIdx());
            case OFFICIAL_C2C -> this.user.sendMessage(rt, markdown, this.message.getRefIdx(), actionButton);
            default ->
                    throw new UnsupportedPlatform(this.user.getPlatform(), "sendMessage(Markdown markdown, boolean at, boolean ref)");
        };
    }

    @Override
    public String sendStreamTextMessage(List<String> textDeltas) {
        if (Objects.requireNonNull(this.user.getPlatform()) == Platform.OFFICIAL_C2C) {
            return C2CChat.replyTextStreamDeltas(this.user.getUserId(), RT.message(this.message.getMessageId()), textDeltas);
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendStreamTextMessage(List<String> textDeltas)");
    }

    @Override
    public String sendStreamMarkdownMessage(List<Markdown> markdownDeltas) {
        if (Objects.requireNonNull(this.user.getPlatform()) == Platform.OFFICIAL_C2C) {
            return C2CChat.replyStreamDeltas(this.user.getUserId(), RT.message(this.message.getMessageId()), markdownDeltas);
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "sendStreamMarkdownMessage(List<Markdown> markdownDeltas)");
    }

    @Override
    public boolean recall() {
        return message.recall();
    }

    @Override
    public boolean recall(String messageId) {
        switch (this.user.getPlatform()) {
            case OFFICIAL_GROUP -> {
                return GroupChat.recallMessage(this.groupId, messageId);
            }
            case OFFICIAL_C2C -> {
                return C2CChat.recallMessage(this.user.getUserId(), messageId);
            }
        }
        throw new UnsupportedPlatform(this.user.getPlatform(), "recall(String messageId)");
    }
}
