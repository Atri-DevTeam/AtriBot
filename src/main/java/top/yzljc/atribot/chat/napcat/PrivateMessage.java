package top.yzljc.atribot.chat.napcat;

import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;

import java.util.Collection;
import java.util.LinkedList;

/**
 * @Author YZ_Ljc_
 * @ClassName PrivateMessage
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.chat
 *
 * <ul>
 *   <li>业务层不直接依赖 {@code MessageUtils}，通过本类完成私聊发送</li>
 *   <li>提供文本、图片、回复、转发等常见私聊场景的统一 API</li>
 *   <li>发送方法统一返回 {@code messageId}，失败返回 {@code null}</li>
 * </ul>
 *
 * <p>常用场景：</p>
 * <ul>
 *   <li>发送纯文本：{@link #chatMessage(String, String)}</li>
 *   <li>发送图片：{@link #chatMessage(String, String, MessageUtils.ImageType)}</li>
 *   <li>回复某条私聊：{@link #replyMessage(String, String, String)} / {@link #replyMessage(String, String, Collection)}</li>
 *   <li>发送合并转发：{@link #forwardMessage(String, Collection, String, String, String...)}</li>
 * </ul>
 *
 * <p>关于节点构造：</p>
 * <ul>
 *   <li>{@link #createTextNode(String)} / {@link #createImageNode(String)} 用于构造 forward 的 node 段</li>
 *   <li>需要自定义 node 展示身份时，使用带 {@code uin/name} 的重载</li>
 * </ul>
 */
public class PrivateMessage {
    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String userId, Collection<MessageSegment> data) {
        BotRuntimeData.callPrivateMessageSend();
        return MessageUtils.privateMessage(userId, data);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String userId, String text) {
        BotRuntimeData.callPrivateMessageSend();
        return MessageUtils.privateMessage(userId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String chatMessage(String userId, String imgData, MessageUtils.ImageType type) {
        BotRuntimeData.callPrivateMessageSend();
        return MessageUtils.privateMessage(userId, imgData, type);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String userId, String messageId, String text) {
        BotRuntimeData.callPrivateMessageSend();
        return MessageUtils.replyPrivateMessage(userId, messageId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String userId, String messageId, Collection<MessageSegment> messageSegments) {
        BotRuntimeData.callPrivateMessageSend();
        return MessageUtils.replyPrivateMessage(userId, messageId, messageSegments);
    }

    public static void recallMessage(String messageId) {
        BotRuntimeData.callRecallMessage();
        MessageUtils.recallMessage(messageId);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String forwardMessage(String userId, Collection<MessageSegment> nodes, String title, String summary, String... textVars) {
        BotRuntimeData.callPrivateMessageSend();
        return MessageUtils.sendPrivateForwardMessage(userId, nodes, title, summary, textVars);
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

    public static MessageSegment createTextNode(LinkedList<MessageSegment> text) {
        return MessageUtils.createTextNodeSegment(text);
    }

    public static MessageSegment createImageNode(String url) {
        return MessageUtils.createImageNodeSegment(url);
    }

    public static MessageSegment createImageNode(String url, String uin, String name) {
        return MessageUtils.createImageNodeSegment(url, uin, name);
    }
}
