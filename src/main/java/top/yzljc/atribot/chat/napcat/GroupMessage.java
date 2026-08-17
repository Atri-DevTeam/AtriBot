package top.yzljc.atribot.chat.napcat;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;

import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupMessage
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.chat
 *
 * <ul>
 *   <li>业务代码不直接依赖 {@code MessageUtils}，统一经由本类调用</li>
 *   <li>按场景提供清晰重载：文本、图片、组合消息、回复、转发、@</li>
 *   <li>发送方法统一返回 {@code messageId}，失败返回 {@code null}</li>
 * </ul>
 *
 * <p>常用场景：</p>
 * <ul>
 *   <li>发送纯文本：{@link #chatMessage(String, String)}</li>
 *   <li>发送图片：{@link #chatMessage(String, ImageComponent)}</li>
 *   <li>发送文本+图片：通过 {@link ImageComponent#setText(String)} 设置文本</li>
 *   <li>回复某条消息：{@link #replyMessage(String, String, String)} / {@link #replyMessage(String, String, Collection)}</li>
 *   <li>转发单条消息：{@link #forwardTo(String, String)}</li>
 *   <li>发送合并转发：{@link #forwardMessage(String, Collection, String, String, String...)}</li>
 * </ul>
 *
 * <p>关于 whetherAt：</p>
 * <ul>
 *   <li>带 {@code whetherAt=true} 的重载会自动拼出 {@code @User + 空格 + 正文}</li>
 * </ul>
 *
 * <p>关于节点构造：</p>
 * <ul>
 *   <li>{@link #createTextNode(String)} / {@link #createImageNode(String)} 用于构造 forward 的 node 段</li>
 *   <li>默认使用内置展示身份；如需自定义展示身份，使用带 {@code uin/name} 的重载</li>
 * </ul>
 */
public class GroupMessage {
    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String groupId, String text) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.groupMessage(groupId, List.of(new MessageSegment("text", Map.of("text", text))));
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String userId, String groupId, String text, boolean whetherAt) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.chatMessage(userId, groupId, text, whetherAt);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String groupId, List<MessageSegment> data) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.groupMessage(groupId, data);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String userId, String groupId, List<MessageSegment> data, boolean whetherAt) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.chatMessage(userId, groupId, data, whetherAt);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String groupId, ImageComponent image) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.sendSingleImageGroupMessage(groupId, image);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String userId, String groupId, ImageComponent image, boolean whetherAt) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.chatMessage(userId, groupId, image, whetherAt);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupId, String messageId, String text) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage("0", groupId, messageId, false, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupId, String messageId, Collection<MessageSegment> messageSegment) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage("0", groupId, messageId, false, messageSegment);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, String text) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage(userId, groupId, messageId, whetherAt, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, Collection<MessageSegment> messageSegment) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage(userId, groupId, messageId, whetherAt, messageSegment);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupId, String messageId, ImageComponent image) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage("0", groupId, messageId, false, image);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, ImageComponent image) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage(userId, groupId, messageId, whetherAt, image);
    }

    public static boolean recallMessage(String messageId) {
        BotRuntimeData.callRecallMessage();
        return MessageUtils.recallMessage(messageId);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String forwardTo(String groupId, String messageId) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.forwardSingleGroupMsg(groupId, messageId);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String forwardMessage(String groupId, Collection<MessageSegment> nodes, String title, String summary, String... textVars) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.sendGroupForwardMessage(groupId, nodes, title, summary, textVars);
    }

    public static void atUser(String userId, String groupId, String text) {
        BotRuntimeData.callGroupMessageSend(groupId);
        MessageUtils.atUser(userId, groupId, text);
    }

    // ──────────────────────────────────
    // Node builders (for forward messages)
    // ──────────────────────────────────

    public static MessageSegment createTextNode(String text) {
        return MessageUtils.createTextNodeSegment(text);
    }

    public static MessageSegment createTextNode(String text, String uin, String name) {
        return MessageUtils.createTextNodeSegment(text, uin, name);
    }

    public static MessageSegment createTextNode(MessageSegment text) {
        return MessageUtils.createTextNodeSegment(text);
    }

    public static MessageSegment createTextNode(MessageSegment text, String uin, String name) {
        return MessageUtils.createTextNodeSegment(text, uin, name);
    }

    public static MessageSegment createTextNode(LinkedList<MessageSegment> text) {
        return MessageUtils.createTextNodeSegment(text);
    }

    public static MessageSegment createTextNode(LinkedList<MessageSegment> text, String uin, String name) {
        return MessageUtils.createTextNodeSegment(text, uin, name);
    }

    public static MessageSegment createImageNode(String url) {
        return MessageUtils.createImageNodeSegment(url);
    }

    public static MessageSegment createImageNode(String url, String uin, String name) {
        return MessageUtils.createImageNodeSegment(url, uin, name);
    }

    public static MessageSegment createVideoNode(String url) {
        return MessageUtils.createVideoNodeSegment(url);
    }

    public static MessageSegment createVideoNode(String url, String uin, String name) {
        return MessageUtils.createVideoNodeSegment(url, uin, name);
    }

    public static void handleRequest(boolean approve, String flag, String reason) {
        MessageUtils.handleGroupRequest(approve, flag, reason);
    }

    public static void setEmoji(String messageId, int emojiId, boolean set) {
        MessageUtils.setEmoji(messageId, emojiId, set);
    }
}
