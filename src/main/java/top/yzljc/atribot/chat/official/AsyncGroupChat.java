package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.platform.qq.FileType;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.concurrent.CompletableFuture;
import java.util.Objects;

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

    /**
     * 异步主动发送群聊图片并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param image       图片组件
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @return 消息 ID，发送失败返回 null；普通上传失败沿用文字回退
     */
    public static CompletableFuture<String> sendMessage(String groupOpenId, ImageComponent image, String refIdx) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().groupFileUrl(groupOpenId), image.getType(), image.getData(),
                                "群聊主动", null, image.getText(), refIdx))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }

    /**
     * 异步主动发送带键盘的群聊 Markdown 并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param markdown    Markdown 消息内容
     * @param keyboard    键盘按钮对象，无键盘时传入 null
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String groupOpenId, Markdown markdown, Object keyboard, String refIdx) {
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(markdown, keyboard, null, refIdx));
    }

    /**
     * 异步主动发送群聊图片并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param image       图片组件
     * @return 消息 ID，发送失败返回 null；普通上传失败沿用文字回退
     */
    public static CompletableFuture<String> refMessage(String groupOpenId, String refIdx, ImageComponent image) {
        return sendMessage(groupOpenId, image, refIdx);
    }

    /**
     * 异步主动发送群聊 Markdown并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param markdown    Markdown 消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> refMessage(String groupOpenId, String refIdx, Markdown markdown) {
        return sendMessage(groupOpenId, markdown, null, refIdx);
    }

    /**
     * 异步主动发送带键盘的群聊 Markdown 并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param markdown    Markdown 消息内容
     * @param keyboard    键盘按钮对象，无键盘时传入 null
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> refMessage(String groupOpenId, String refIdx, Markdown markdown, Object keyboard) {
        return sendMessage(groupOpenId, markdown, keyboard, refIdx);
    }


    /**
     * 异步回复群聊 Markdown 消息并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象，无键盘时传入 null
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Markdown markdown, Object keyboard, String refIdx) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(markdown, keyboard, rt, refIdx));
    }

    /**
     * 异步回复群聊 Markdown 消息并引用指定消息，同时 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param userOpenId  被 @ 的用户 openId
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象，无键盘时传入 null
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown, Object keyboard, String refIdx) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGroupMessageAsync(groupOpenId,
                service().getBodyFactory().markdown(service().getBodyFactory().atMarkdown(userOpenId, markdown), keyboard, rt, refIdx));
    }

    /**
     * 异步回复群聊图片消息并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image       图片组件
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, ImageComponent image, String refIdx) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().groupFileUrl(groupOpenId), image.getType(), image.getData(),
                                (rt instanceof RT.Event ? "群聊事件" : "群聊"), rt, image.getText(), refIdx))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }


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
     * 异步发送群聊 Ark23 主动消息
     *
     * @param groupOpenId 群 openId
     * @param ark         Ark23 消息体
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String groupOpenId, Ark23 ark) {
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().ark23(ark));
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
                                service().groupFileUrl(groupOpenId), image.getType(), image.getData(), "群聊主动", null, image.getText()))
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
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param replyText   回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String replyText) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().replyText(rt, replyText));
    }

    /**
     * 异步回复群聊 Ark23 消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param ark         Ark23 消息体
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Ark23 ark) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().ark23(ark, rt));
    }

    /**
     * 异步引用回复群聊纯文本消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param replyText   回复内容
     * @param refIdx      被引用消息的索引 ID
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String replyText, String refIdx) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGroupMessageAsync(groupOpenId, service().getBodyFactory().replyTextRef(rt, replyText, refIdx));
    }

    /**
     * 异步回复群聊 Markdown 消息并 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param userOpenId  被 @ 的用户 openId
     * @param markdown    Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown) {
        return replyMessage(groupOpenId, rt, userOpenId, markdown, null, null);
    }

    /**
     * 异步回复群聊 Markdown 消息（不 @）
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown    Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Markdown markdown) {
        return replyMessage(groupOpenId, rt, markdown, null, null);
    }

    /**
     * 异步回复带键盘的群聊 Markdown 消息并 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param userOpenId  被 @ 的用户 openId
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown, Object keyboard) {
        return replyMessage(groupOpenId, rt, userOpenId, markdown, keyboard, null);
    }

    /**
     * 异步回复带键盘的群聊 Markdown 消息（不 @）
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Markdown markdown, Object keyboard) {
        return replyMessage(groupOpenId, rt, markdown, keyboard, null);
    }

    /**
     * 异步回复群聊图片消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, ImageComponent image) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().groupFileUrl(groupOpenId), image.getType(), image.getData(), (rt instanceof RT.Event ? "群聊事件" : "群聊"), rt, image.getText()))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }

    /**
     * 异步回复群聊文件消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param fileType    文件类型
     * @param value       文件 URL
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, FileType fileType, String value) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildFileRequest(
                                service().groupFileUrl(groupOpenId), fileType, value, "文件发送", rt))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }

    /**
     * 异步回复群聊语音消息，上传失败时不发送文本回退
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param url         语音文件 URL
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyAudioMessage(String groupOpenId, RT rt, String url) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return ThreadManager.supplyAsync(() -> service().getMediaUploader().buildFileRequest(
                        service().groupFileUrl(groupOpenId), FileType.AUDIO, url, "听声辨物语音", rt, true))
                .thenCompose(request -> request == null ? CompletableFuture.completedFuture(null)
                        : service().sendGroupMessageAsync(groupOpenId, request));
    }

    /**
     * 异步主动发送群聊纯文本并引用消息（不携带被动回复来源）
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
