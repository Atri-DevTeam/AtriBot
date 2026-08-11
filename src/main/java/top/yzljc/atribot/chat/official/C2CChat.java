package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @Author YZ_Ljc_
 * @ClassName C2CChat
 * @Created_at 2026/06/09
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 * @Description
 * 单聊同步业务层
 * 内部委托 {@link AsyncC2CChat} 执行异步业务逻辑
 * 如需非阻塞调用请直接使用 {@link AsyncC2CChat}
 */
public final class C2CChat {

    /**
     * 发送单聊纯文本主动消息
     *
     * @param openId 用户 openId
     * @param text   消息内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, String text) {
        return await(AsyncC2CChat.sendMessage(openId, text));
    }

    /**
     * 发送单聊 Markdown 主动消息
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息体
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, Markdown markdown) {
        return await(AsyncC2CChat.sendMessage(openId, markdown));
    }

    /**
     * 发送带键盘的单聊 Markdown 主动消息
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息体
     * @param keyboard 键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, Markdown markdown, Object keyboard) {
        return await(AsyncC2CChat.sendMessage(openId, markdown, keyboard));
    }

    /**
     * 发送单聊图片主动消息
     *
     * @param openId 用户 openId
     * @param image  图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, ImageComponent image) {
        return await(AsyncC2CChat.sendMessage(openId, image));
    }

    /**
     * 回复单聊纯文本消息
     *
     * @param openId    用户 openId
     * @param msgId     被回复的消息 ID
     * @param replyText 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, String replyText) {
        return await(AsyncC2CChat.replyMessage(openId, msgId, replyText));
    }

    /**
     * 引用回复单聊纯文本消息
     *
     * @param openId  用户 openId
     * @param refIdx  被引用消息的索引 ID
     * @param content 引用回复的文本内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String refMessage(String openId, String refIdx, String content) {
        return await(AsyncC2CChat.refMessage(openId, refIdx, content));
    }

    /**
     * 回复单聊 Markdown 消息
     *
     * @param openId   用户 openId
     * @param msgId    被回复的消息 ID
     * @param markdown Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, Markdown markdown) {
        return await(AsyncC2CChat.replyMessage(openId, msgId, markdown));
    }

    /**
     * 回复带键盘的单聊 Markdown 消息
     *
     * @param openId   用户 openId
     * @param msgId    被回复的消息 ID
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, Markdown markdown, Object keyboard) {
        return await(AsyncC2CChat.replyMessage(openId, msgId, markdown, keyboard));
    }

    /**
     * 回复单聊图片消息
     *
     * @param openId 用户 openId
     * @param msgId  被回复的消息 ID
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, ImageComponent image) {
        return await(AsyncC2CChat.replyMessage(openId, msgId, image));
    }

    /**
     * 回复单聊事件（Markdown）
     *
     * @param openId  用户 openId
     * @param eventId 事件 ID
     * @param markdown Markdown 内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String openId, String eventId, Markdown markdown) {
        return await(AsyncC2CChat.replyEventMessage(openId, eventId, markdown));
    }

    /**
     * 回复带键盘的单聊事件（Markdown）
     *
     * @param openId   用户 openId
     * @param eventId  事件 ID
     * @param markdown Markdown 内容
     * @param keyboard 键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String openId, String eventId, Markdown markdown, Object keyboard) {
        return await(AsyncC2CChat.replyEventMessage(openId, eventId, markdown, keyboard));
    }

    /**
     * 回复单聊事件（纯文本）
     *
     * @param openId  用户 openId
     * @param eventId 事件 ID
     * @param text    文本内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String openId, String eventId, String text) {
        return await(AsyncC2CChat.replyEventMessage(openId, eventId, text));
    }

    /**
     * 回复单聊事件（图片）
     *
     * @param openId  用户 openId
     * @param eventId 事件 ID
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String openId, String eventId, ImageComponent image) {
        return await(AsyncC2CChat.replyEventMessage(openId, eventId, image));
    }

    /**
     * 发送单聊 Markdown 流式消息（新建消息）
     *
     * @param openId         用户 openId
     * @param markdownDeltas Markdown 增量列表
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String streamDeltas(String openId, List<Markdown> markdownDeltas) {
        return await(AsyncC2CChat.streamDeltas(openId, markdownDeltas));
    }

    /**
     * 回复单聊 Markdown 流式消息（追加到已有消息）
     *
     * @param openId         用户 openId
     * @param msgId          被回复的消息 ID
     * @param markdownDeltas Markdown 增量列表
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyStreamDeltas(String openId, String msgId, List<Markdown> markdownDeltas) {
        return await(AsyncC2CChat.replyStreamDeltas(openId, msgId, markdownDeltas));
    }

    /**
     * 回复单聊事件 Markdown 流式消息
     *
     * @param openId         用户 openId
     * @param eventId        事件 ID
     * @param markdownDeltas Markdown 增量列表
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventStreamDeltas(String openId, String eventId, List<Markdown> markdownDeltas) {
        return await(AsyncC2CChat.replyEventStreamDeltas(openId, eventId, markdownDeltas));
    }

    /**
     * 发送单聊纯文本流式消息
     *
     * @param openId     用户 openId
     * @param textDeltas 文本增量列表
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String streamTextDeltas(String openId, List<String> textDeltas) {
        return await(AsyncC2CChat.streamTextDeltas(openId, textDeltas));
    }

    /**
     * 回复单聊纯文本流式消息
     *
     * @param openId     用户 openId
     * @param msgId      被回复的消息 ID
     * @param textDeltas 文本增量列表
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyTextStreamDeltas(String openId, String msgId, List<String> textDeltas) {
        return await(AsyncC2CChat.replyTextStreamDeltas(openId, msgId, textDeltas));
    }

    /**
     * 回复单聊事件纯文本流式消息
     *
     * @param openId     用户 openId
     * @param eventId    事件 ID
     * @param textDeltas 文本增量列表
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventTextStreamDeltas(String openId, String eventId, List<String> textDeltas) {
        return await(AsyncC2CChat.replyEventTextStreamDeltas(openId, eventId, textDeltas));
    }

    /**
     * 撤回单聊消息
     *
     * @param openId    用户 openId
     * @param messageId 消息 ID
     */
    public static boolean recallMessage(String openId, String messageId) {
        return Atri.getInstance().getChatService().recallPrivateMessage(openId, messageId);
    }

    private static String await(CompletableFuture<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }
}
