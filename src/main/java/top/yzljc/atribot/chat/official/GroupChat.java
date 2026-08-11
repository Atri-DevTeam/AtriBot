package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.platform.qq.FileType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupChat
 * @Created_at 2026/06/09
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 * @Description
 * 群聊同步业务层
 * 内部委托 {@link AsyncGroupChat} 执行异步业务逻辑
 * 如需非阻塞调用请直接使用 {@link AsyncGroupChat}
 */
public final class GroupChat {

    /**
     * 发送群聊纯文本主动消息
     *
     * @param groupOpenId 群 openId
     * @param text        消息内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, String text) {
        return await(AsyncGroupChat.sendMessage(groupOpenId, text));
    }

    /**
     * 发送群聊 Markdown 主动消息
     *
     * @param groupOpenId 群 openId
     * @param markdown    Markdown 消息体
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, Markdown markdown) {
        return await(AsyncGroupChat.sendMessage(groupOpenId, markdown));
    }

    /**
     * 发送群聊图片主动消息
     *
     * @param groupOpenId 群 openId
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, ImageComponent image) {
        return await(AsyncGroupChat.sendMessage(groupOpenId, image));
    }

    /**
     * 发送带键盘的群聊 Markdown 主动消息
     *
     * @param groupOpenId 群 openId
     * @param markdown    Markdown 消息体
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, Markdown markdown, Object keyboard) {
        return await(AsyncGroupChat.sendMessage(groupOpenId, markdown, keyboard));
    }

    /**
     * 回复群聊纯文本消息
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param replyText   回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String msgId, String replyText) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, msgId, replyText));
    }

    /**
     * 回复群聊 Markdown 消息并 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param userOpenId  被 @ 的用户 openId
     * @param msgId       被回复的消息 ID
     * @param markdown    Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, userOpenId, msgId, markdown));
    }

    /**
     * 回复群聊 Markdown 消息（不 @）
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param markdown    Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String msgId, Markdown markdown) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, msgId, markdown));
    }

    /**
     * 回复带键盘的群聊 Markdown 消息并 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param userOpenId  被 @ 的用户 openId
     * @param msgId       被回复的消息 ID
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown, Object keyboard) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, userOpenId, msgId, markdown, keyboard));
    }

    /**
     * 回复带键盘的群聊 Markdown 消息（不 @）
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String msgId, Markdown markdown, Object keyboard) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, msgId, markdown, keyboard));
    }

    /**
     * 回复群聊图片消息
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String msgId, ImageComponent image) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, msgId, image));
    }

    /**
     * 回复群聊文件消息
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param fileType    文件类型
     * @param value       文件 URL
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String msgId, FileType fileType, String value) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, msgId, fileType, value));
    }

    /**
     * 回复群聊事件（Markdown，自动 @ 消息发送者）
     *
     * @param groupOpenId  群 openId
     * @param memberOpenId 事件发送者 openId
     * @param eventId      事件 ID
     * @param markdown     Markdown 内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String groupOpenId, String memberOpenId, String eventId, Markdown markdown) {
        return await(AsyncGroupChat.replyEventMessage(groupOpenId, memberOpenId, eventId, markdown));
    }

    /**
     * 回复带键盘的群聊事件（Markdown，自动 @ 消息发送者）
     *
     * @param groupOpenId  群 openId
     * @param memberOpenId 事件发送者 openId
     * @param eventId      事件 ID
     * @param markdown     Markdown 内容
     * @param buttons      键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String groupOpenId, String memberOpenId, String eventId, Markdown markdown, Object buttons) {
        return await(AsyncGroupChat.replyEventMessage(groupOpenId, memberOpenId, eventId, markdown, buttons));
    }

    /**
     * 回复群聊事件（Markdown，不 @）
     *
     * @param groupOpenId 群 openId
     * @param eventId     事件 ID
     * @param markdown    Markdown 内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String groupOpenId, String eventId, Markdown markdown) {
        return await(AsyncGroupChat.replyEventMessage(groupOpenId, eventId, markdown));
    }

    /**
     * 回复带键盘的群聊事件（Markdown，不 @）
     *
     * @param groupOpenId 群 openId
     * @param eventId     事件 ID
     * @param markdown    Markdown 内容
     * @param buttons     键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String groupOpenId, String eventId, Markdown markdown, Object buttons) {
        return await(AsyncGroupChat.replyEventMessage(groupOpenId, eventId, markdown, buttons));
    }

    /**
     * 回复群聊事件（纯文本）
     *
     * @param groupOpenId 群 openId
     * @param eventId     事件 ID
     * @param text        文本内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String groupOpenId, String eventId, String text) {
        return await(AsyncGroupChat.replyEventMessage(groupOpenId, eventId, text));
    }

    /**
     * 回复群聊事件（图片）
     *
     * @param groupOpenId 群 openId
     * @param eventId     事件 ID
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyEventMessage(String groupOpenId, String eventId, ImageComponent image) {
        return await(AsyncGroupChat.replyEventMessage(groupOpenId, eventId, image));
    }

    /**
     * 引用回复群聊纯文本消息
     *
     * @param groupOpenId 群 openId
     * @param refIdx      被引用消息的索引 ID
     * @param content     引用回复的文本内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String refMessage(String groupOpenId, String refIdx, String content) {
        return await(AsyncGroupChat.refMessage(groupOpenId, refIdx, content));
    }

    /**
     * 撤回群聊消息
     *
     * @param groupOpenId 群 openId
     * @param messageId   消息 ID
     */
    public static boolean recallMessage(String groupOpenId, String messageId) {
        return Atri.getInstance().getChatService().recallGroupMessage(groupOpenId, messageId);
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
