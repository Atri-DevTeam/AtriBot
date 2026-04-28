package top.yzljc.qqbot.chat;

import top.yzljc.qqbot.chat.impl.MessageUtils;
import top.yzljc.qqbot.utils.BotRuntimeData;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
 *   <li>发送方法统一返回 {@code messageId}，失败返回 {@code 0L}</li>
 * </ul>
 *
 * <p>常用场景：</p>
 * <ul>
 *   <li>发送纯文本：{@link #chatMessage(long, String)}</li>
 *   <li>发送图片：{@link #chatMessage(long, String, MessageUtils.ImageType)}</li>
 *   <li>发送文本+图片：{@link #chatMessage(long, String, String, MessageUtils.ImageType)}</li>
 *   <li>回复某条消息：{@link #replyMessage(long, long, String)} / {@link #replyMessage(long, long, Collection)}</li>
 *   <li>转发单条消息：{@link #forwardTo(long, long)}</li>
 *   <li>发送合并转发：{@link #forwardMessage(long, Collection, String, String, String...)}</li>
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
    public static long chatMessage(long groupId, String text) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.groupMessage(groupId, List.of(new MessageSegment("text", Map.of("text", text))));
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long chatMessage(long userId, long groupId, String text, boolean whetherAt) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.chatMessage(userId, groupId, text, whetherAt);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long chatMessage(long groupId, List<MessageSegment> data) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.groupMessage(groupId, data);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long chatMessage(long userId, long groupId, List<MessageSegment> data, boolean whetherAt) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.chatMessage(userId, groupId, data, whetherAt);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long chatMessage(long groupId, String imgData, MessageUtils.ImageType type) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.sendSingleImageGroupMessage(groupId, imgData, type);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long chatMessage(long groupId, String text, String imgData, MessageUtils.ImageType type) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.groupTextImageMessage(groupId, text, imgData, type);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long chatMessage(long userId, long groupId, String imgData, MessageUtils.ImageType type, boolean whetherAt) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.chatMessage(userId, groupId, imgData, type, whetherAt);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long replyMessage(long groupId, long messageId, String text) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage(0, groupId, messageId, false, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long replyMessage(long groupId, long messageId, Collection<MessageSegment> messageSegment) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage(0, groupId, messageId, false, messageSegment);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long replyMessage(long userId, long groupId, long messageId, boolean whetherAt, String text) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage(userId, groupId, messageId, whetherAt, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long replyMessage(long userId, long groupId, long messageId, boolean whetherAt, Collection<MessageSegment> messageSegment) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.replyMessage(userId, groupId, messageId, whetherAt, messageSegment);
    }

    public static void recallMessage(long messageId) {
        BotRuntimeData.callRecallMessage();
        MessageUtils.recallMessage(messageId);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long forwardTo(long groupId, long messageId) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.forwardSingleGroupMsg(groupId, messageId);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long forwardMessage(long groupId, Collection<MessageSegment> nodes, String title, String summary, String... textVars) {
        BotRuntimeData.callGroupMessageSend(groupId);
        return MessageUtils.sendGroupForwardMessage(groupId, nodes, title, summary, textVars);
    }

    public static void atUser(long userId, long groupId, String text) {
        BotRuntimeData.callGroupMessageSend(groupId);
        MessageUtils.atUser(userId, groupId, text);
    }

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

    public static void handleRequest(boolean approve, String flag, String reason) {
        MessageUtils.handleGroupRequest(approve, flag, reason);
    }
}