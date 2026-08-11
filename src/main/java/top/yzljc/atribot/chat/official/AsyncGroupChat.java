package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.platform.qq.FileType;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.concurrent.CompletableFuture;

/**
 * @Author YZ_Ljc_
 * @ClassName AsyncGroupChat
 * @Created_at 2026/07/24
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 * @Description
 * 群聊异步业务层
 * 封装所有群聊场景下的消息构造与发送逻辑
 * 包括主动消息、被动回复、事件回复和引用回复
 * 底层 HTTP 传输由 {@link ChatService} 提供
 */
public final class AsyncGroupChat {

    private static ChatService service() {
        return Atri.getInstance().getChatService();
    }

    /**
     * 异步发送群聊纯文本主动消息
     *
     * @param groupOpenId 群 openId
     * @param text        消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String groupOpenId, String text) {
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().text(text));
    }

    /**
     * 异步发送群聊 Markdown 主动消息
     *
     * @param groupOpenId 群 openId
     * @param markdown    Markdown 消息体
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String groupOpenId, Markdown markdown) {
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().markdown(markdown));
    }

    /**
     * 异步发送群聊图片主动消息
     *
     * @param groupOpenId 群 openId
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String groupOpenId, ImageComponent image) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().groupFileUrl(groupOpenId), image.getType(), image.getData(), "群聊主动", null, null, image.getText()))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }

    /**
     * 异步发送带键盘的群聊 Markdown 主动消息
     *
     * @param groupOpenId 群 openId
     * @param markdown    Markdown 消息体
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String groupOpenId, Markdown markdown, Object keyboard) {
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().markdown(markdown, keyboard));
    }

    /**
     * 异步回复群聊纯文本消息
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param replyText   回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, String msgId, String replyText) {
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().replyText(msgId, replyText));
    }

    /**
     * 异步回复群聊 Markdown 消息并 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param userOpenId  被 @ 的用户 openId
     * @param msgId       被回复的消息 ID
     * @param markdown    Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(
                        service().getBodyFactory().atMarkdown(userOpenId, markdown), null, msgId, null));
    }

    /**
     * 异步回复群聊 Markdown 消息（不 @）
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param markdown    Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, String msgId, Markdown markdown) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(markdown.getText(), null, msgId, null));
    }

    /**
     * 异步回复带键盘的群聊 Markdown 消息并 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param userOpenId  被 @ 的用户 openId
     * @param msgId       被回复的消息 ID
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown, Object keyboard) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(
                        service().getBodyFactory().atMarkdown(userOpenId, markdown), keyboard, msgId, null));
    }

    /**
     * 异步回复带键盘的群聊 Markdown 消息（不 @）
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, String msgId, Markdown markdown, Object keyboard) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(markdown.getText(), keyboard, msgId, null));
    }

    /**
     * 异步回复群聊图片消息
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, String msgId, ImageComponent image) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().groupFileUrl(groupOpenId), image.getType(), image.getData(), "群聊", msgId, null, image.getText()))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }

    /**
     * 异步回复群聊文件消息
     *
     * @param groupOpenId 群 openId
     * @param msgId       被回复的消息 ID
     * @param fileType    文件类型
     * @param value       文件 URL
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, String msgId, FileType fileType, String value) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildFileRequest(
                                service().groupFileUrl(groupOpenId), fileType, value, "文件发送", msgId))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }

    /**
     * 异步回复群聊事件（Markdown，自动 @ 消息发送者）
     *
     * @param groupOpenId  群 openId
     * @param memberOpenId 事件发送者 openId
     * @param eventId      事件 ID
     * @param markdown     Markdown 内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String groupOpenId, String memberOpenId, String eventId, Markdown markdown) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(
                        service().getBodyFactory().atMarkdown(memberOpenId, markdown), null, null, eventId));
    }

    /**
     * 异步回复带键盘的群聊事件（Markdown，自动 @ 消息发送者）
     *
     * @param groupOpenId  群 openId
     * @param memberOpenId 事件发送者 openId
     * @param eventId      事件 ID
     * @param markdown     Markdown 内容
     * @param buttons      键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String groupOpenId, String memberOpenId, String eventId, Markdown markdown, Object buttons) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(
                        service().getBodyFactory().atMarkdown(memberOpenId, markdown), buttons, null, eventId));
    }

    /**
     * 异步回复群聊事件（Markdown，不 @）
     *
     * @param groupOpenId 群 openId
     * @param eventId     事件 ID
     * @param markdown    Markdown 内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String groupOpenId, String eventId, Markdown markdown) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(markdown.getText(), null, null, eventId));
    }

    /**
     * 异步回复带键盘的群聊事件（Markdown，不 @）
     *
     * @param groupOpenId 群 openId
     * @param eventId     事件 ID
     * @param markdown    Markdown 内容
     * @param buttons     键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String groupOpenId, String eventId, Markdown markdown, Object buttons) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(markdown.getText(), buttons, null, eventId));
    }

    /**
     * 异步回复群聊事件（纯文本）
     *
     * @param groupOpenId 群 openId
     * @param eventId     事件 ID
     * @param text        文本内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String groupOpenId, String eventId, String text) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().eventText(eventId, text));
    }

    /**
     * 异步回复群聊事件（图片）
     *
     * @param groupOpenId 群 openId
     * @param eventId     事件 ID
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String groupOpenId, String eventId, ImageComponent image) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().groupFileUrl(groupOpenId), image.getType(), image.getData(), "群聊事件", null, eventId, image.getText()))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }

    /**
     * 异步引用回复群聊纯文本消息
     *
     * @param groupOpenId 群 openId
     * @param refIdx      被引用消息的索引 ID
     * @param content     引用回复的文本内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> refMessage(String groupOpenId, String refIdx, String content) {
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().textRef(content, refIdx));
    }
}
