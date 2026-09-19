package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.platform.qq.FileType;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @Author YZ_Ljc_
 * @ClassName AsyncC2CChat
 * @Created_at 2026/07/24
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 * @Description
 * 单聊异步业务层
 * 封装所有 C2C（单聊）场景下的消息构造与发送逻辑
 * 包括主动消息、被动回复、事件回复以及流式消息
 * 底层 HTTP 传输由 {@link ChatService} 提供
 */
public final class AsyncC2CChat {

    /**
     * 异步发送单聊正在输入通知，默认持续 60 秒
     *
     * @param openId 用户 openId
     * @return 通知是否发送成功，参数无效、暂停或发送失败时为 false
     */
    public static CompletableFuture<Boolean> sendInputNotify(String openId) {
        return sendInputNotify(openId, 60);
    }

    /**
     * 异步发送单聊正在输入通知，不需要回复来源或引用
     *
     * @param openId      用户 openId
     * @param inputSecond 输入状态持续秒数，必须大于 0
     * @return 通知是否发送成功，参数无效、暂停或发送失败时为 false
     */
    public static CompletableFuture<Boolean> sendInputNotify(String openId, int inputSecond) {
        return service().sendPrivateInputNotifyAsync(openId, inputSecond);
    }

    /**
     * 异步发送单聊纯文本召回消息，不携带回复来源或引用
     *
     * @param openId 用户 openId
     * @param text   纯文本消息内容
     * @return 消息 ID，暂停发送或发送失败返回 null
     */
    public static CompletableFuture<String> wakeupMessage(String openId, String text) {
        return service().sendPrivateWakeupMessageAsync(openId, service().getBodyFactory().text(text));
    }

    /**
     * 异步发送单聊 Ark23 召回消息，不携带回复来源或引用
     *
     * @param openId 用户 openId
     * @param ark    Ark23 消息内容
     * @return 消息 ID，暂停发送或发送失败返回 null
     */
    public static CompletableFuture<String> wakeupMessage(String openId, Ark23 ark) {
        return service().sendPrivateWakeupMessageAsync(openId, service().getBodyFactory().ark23(ark));
    }

    /**
     * 异步发送单聊 Markdown 召回消息，不携带回复来源或引用
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息内容
     * @return 消息 ID，暂停发送或发送失败返回 null
     */
    public static CompletableFuture<String> wakeupMessage(String openId, Markdown markdown) {
        return wakeupMessage(openId, markdown, null);
    }

    /**
     * 异步发送带键盘的单聊 Markdown 召回消息，不携带回复来源或引用
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @return 消息 ID，暂停发送或发送失败返回 null
     */
    public static CompletableFuture<String> wakeupMessage(String openId, Markdown markdown, Object keyboard) {
        return service().sendPrivateWakeupMessageAsync(openId,
                service().getBodyFactory().markdown(markdown, keyboard));
    }

    /**
     * 异步发送单聊图片召回消息，不携带回复来源或引用
     *
     * @param openId 用户 openId
     * @param image  图片组件，支持 URL、Base64 及附带文字
     * @return 消息 ID，暂停发送或发送失败返回 null；普通上传失败沿用文字回退
     */
    public static CompletableFuture<String> wakeupMessage(String openId, ImageComponent image) {
        return ThreadManager.supplyAsync(() -> service().getMediaUploader().buildImageRequest(
                        service().privateFileUrl(openId), image.getType(), image.getData(),
                        "单聊图片召回", null, image.getText()))
                .thenCompose(request -> service().sendPrivateWakeupMessageAsync(openId, request));
    }

    /**
     * 异步发送单聊富媒体召回消息，不携带回复来源或引用
     *
     * @param openId   用户 openId
     * @param fileType 富媒体类型，支持图片、视频、语音和文件
     * @param url      文件 URL
     * @return 消息 ID，暂停发送或发送失败返回 null；语音上传失败不回退为文本，其他类型沿用文字回退
     */
    public static CompletableFuture<String> wakeupMessage(String openId, FileType fileType, String url) {
        Objects.requireNonNull(fileType, "文件类型不能为空");
        return ThreadManager.supplyAsync(() -> service().getMediaUploader().buildFileRequest(
                        service().privateFileUrl(openId), fileType, url, "单聊富媒体召回", null,
                        fileType == FileType.AUDIO))
                .thenCompose(request -> service().sendPrivateWakeupMessageAsync(openId, request));
    }

    /**
     * 异步发送单聊语音召回消息，不携带回复来源或引用
     *
     * @param openId 用户 openId
     * @param url    语音文件 URL
     * @return 消息 ID，暂停发送或上传、发送失败返回 null
     */
    public static CompletableFuture<String> wakeupAudioMessage(String openId, String url) {
        return wakeupMessage(openId, FileType.AUDIO, url);
    }

    /**
     * 异步发送单聊纯文本流式召回消息，不携带回复来源或引用
     *
     * @param openId     用户 openId
     * @param textDeltas 文本增量列表
     * @return 最后成功发送的消息 ID，尚未发送成功时返回 null
     */
    public static CompletableFuture<String> wakeupTextStreamDeltas(String openId, List<String> textDeltas) {
        if (ChatService.isEmergencyPaused()) {
            return CompletableFuture.completedFuture(null);
        }
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, null, C2CStreamMessage.CONTENT_TYPE_TEXT,
                C2CStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(textDeltas), true);
    }

    /**
     * 异步发送单聊 Markdown 流式召回消息，不携带回复来源或引用
     *
     * @param openId         用户 openId
     * @param markdownDeltas Markdown 增量列表
     * @return 最后成功发送的消息 ID，尚未发送成功时返回 null
     */
    public static CompletableFuture<String> wakeupStreamDeltas(String openId, List<Markdown> markdownDeltas) {
        if (ChatService.isEmergencyPaused()) {
            return CompletableFuture.completedFuture(null);
        }
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, null, C2CStreamMessage.CONTENT_TYPE_MARKDOWN,
                C2CStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(markdownTexts(markdownDeltas)), true);
    }


    /**
     * 异步主动发送单聊图片并引用指定消息
     *
     * @param openId 用户 openId
     * @param image  图片组件
     * @param refIdx 被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @return 消息 ID，发送失败返回 null；普通上传失败沿用文字回退
     */
    public static CompletableFuture<String> sendMessage(String openId, ImageComponent image, String refIdx) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().privateFileUrl(openId), image.getType(), image.getData(),
                                "单聊主动", null, image.getText(), refIdx))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendPrivateMessageAsync(openId, request));
    }

    /**
     * 异步主动发送带键盘的单聊 Markdown 并引用指定消息
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String openId, Markdown markdown, Object keyboard, String refIdx) {
        return service().sendPrivateMessageAsync(openId,
                service().getBodyFactory().markdown(markdown, keyboard, null, refIdx));
    }

    /**
     * 异步主动发送单聊图片并引用指定消息
     *
     * @param openId 用户 openId
     * @param refIdx 被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param image  图片组件
     * @return 消息 ID，发送失败返回 null；普通上传失败沿用文字回退
     */
    public static CompletableFuture<String> refMessage(String openId, String refIdx, ImageComponent image) {
        return sendMessage(openId, image, refIdx);
    }

    /**
     * 异步主动发送单聊 Markdown并引用指定消息
     *
     * @param openId   用户 openId
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param markdown Markdown 消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> refMessage(String openId, String refIdx, Markdown markdown) {
        return sendMessage(openId, markdown, null, refIdx);
    }

    /**
     * 异步主动发送带键盘的单聊 Markdown 并引用指定消息
     *
     * @param openId   用户 openId
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param markdown Markdown 消息内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> refMessage(String openId, String refIdx, Markdown markdown, Object keyboard) {
        return sendMessage(openId, markdown, keyboard, refIdx);
    }


    /**
     * 异步回复单聊 Markdown 消息并引用指定消息
     *
     * @param openId   用户 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown, Object keyboard, String refIdx) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendPrivateMessageAsync(openId,
                service().getBodyFactory().markdown(markdown, keyboard, rt, refIdx));
    }

    /**
     * 异步回复单聊图片消息并引用指定消息
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image  图片组件
     * @param refIdx 被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, RT rt, ImageComponent image, String refIdx) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().privateFileUrl(openId), image.getType(), image.getData(),
                                (rt instanceof RT.Event ? "单聊事件" : "单聊"), rt, image.getText(), refIdx))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendPrivateMessageAsync(openId, request));
    }


    private static ChatService service() {
        return Atri.getInstance().getChatService();
    }

    /**
     * 异步回复单聊语音消息，上传失败时不发送文本回退
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param url    语音文件 URL
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyAudioMessage(String openId, RT rt, String url) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return ThreadManager.supplyAsync(() -> service().getMediaUploader().buildFileRequest(
                        service().privateFileUrl(openId), FileType.AUDIO, url, "私聊语音", rt, true))
                .thenCompose(request -> request == null ? CompletableFuture.completedFuture(null)
                        : service().sendPrivateMessageAsync(openId, request));
    }

    /**
     * 异步发送单聊纯文本主动消息
     *
     * @param openId 用户 openId
     * @param text   消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String openId, String text) {
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().text(text));
    }

    /**
     * 异步发送单聊 Ark23 主动消息
     *
     * @param openId 用户 openId
     * @param ark    Ark23 消息体
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String openId, Ark23 ark) {
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().ark23(ark));
    }

    /**
     * 异步发送单聊 Markdown 主动消息
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息体
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String openId, Markdown markdown) {
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().markdown(markdown));
    }

    /**
     * 异步发送带键盘的单聊 Markdown 主动消息
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息体
     * @param keyboard 键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String openId, Markdown markdown, Object keyboard) {
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().markdown(markdown, keyboard));
    }

    /**
     * 异步发送单聊图片主动消息
     *
     * @param openId 用户 openId
     * @param image  图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String openId, ImageComponent image) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().privateFileUrl(openId), image.getType(), image.getData(), "单聊主动", null, image.getText()))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendPrivateMessageAsync(openId, request));
    }

    /**
     * 异步回复单聊纯文本消息
     *
     * @param openId    用户 openId
     * @param rt        消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param replyText 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, RT rt, String replyText) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().replyText(rt, replyText));
    }

    /**
     * 异步回复单聊纯文本消息并引用指定消息
     *
     * @param openId    用户 openId
     * @param rt        消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param replyText 回复内容
     * @param refIdx    被引用消息的索引 ID，不改变消息或事件回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, RT rt, String replyText, String refIdx) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().replyTextRef(rt, replyText, refIdx));
    }

    /**
     * 异步回复单聊 Ark23 消息
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param ark    Ark23 消息体
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, RT rt, Ark23 ark) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().ark23(ark, rt));
    }

    /**
     * 异步引用回复单聊纯文本消息
     *
     * <p>与被动回复（replyMessage）不同，这里发的是带 message_reference 的主动消息，
     * 客户端会把被引用的那条渲染成可点的引用块。
     *
     * @param openId  用户 openId
     * @param refIdx  被引用消息的索引 ID
     * @param content 引用回复的文本内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> refMessage(String openId, String refIdx, String content) {
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().textRef(content, refIdx));
    }

    /**
     * 异步回复单聊 Markdown 消息
     *
     * @param openId   用户 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown) {
        return replyMessage(openId, rt, markdown, null, null);
    }

    /**
     * 异步回复带键盘的单聊 Markdown 消息
     *
     * @param openId   用户 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown, Object keyboard) {
        return replyMessage(openId, rt, markdown, keyboard, null);
    }

    /**
     * 异步回复单聊图片消息
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image  图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, RT rt, ImageComponent image) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().privateFileUrl(openId), image.getType(), image.getData(), (rt instanceof RT.Event ? "单聊事件" : "单聊"), rt, image.getText()))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendPrivateMessageAsync(openId, request));
    }

    /**
     * 异步发送单聊 Markdown 流式消息（新建消息）
     *
     * @param openId         用户 openId
     * @param markdownDeltas Markdown 增量列表
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> streamDeltas(String openId, List<Markdown> markdownDeltas) {
        if (ChatService.isEmergencyPaused()) {
            return CompletableFuture.completedFuture(null);
        }
        List<String> texts = markdownTexts(markdownDeltas);
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, null,
                C2CStreamMessage.CONTENT_TYPE_MARKDOWN,
                C2CStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(texts), null);
    }

    /**
     * 异步回复单聊 Markdown 流式消息（由 RT 指定触发来源）
     *
     * @param openId         用户 openId
     * @param rt             消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdownDeltas Markdown 增量列表
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyStreamDeltas(String openId, RT rt, List<Markdown> markdownDeltas) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        if (ChatService.isEmergencyPaused()) {
            return service().sendPrivateMaintenanceMessageAsync(openId, rt);
        }
        List<String> texts = markdownTexts(markdownDeltas);
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, rt,
                C2CStreamMessage.CONTENT_TYPE_MARKDOWN,
                C2CStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(texts), null);
    }

    /**
     * 异步发送单聊纯文本流式消息
     *
     * @param openId     用户 openId
     * @param textDeltas 文本增量列表
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> streamTextDeltas(String openId, List<String> textDeltas) {
        if (ChatService.isEmergencyPaused()) {
            return CompletableFuture.completedFuture(null);
        }
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, null,
                C2CStreamMessage.CONTENT_TYPE_TEXT,
                C2CStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(textDeltas), null);
    }

    /**
     * 异步回复单聊纯文本流式消息
     *
     * @param openId     用户 openId
     * @param rt         消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param textDeltas 文本增量列表
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyTextStreamDeltas(String openId, RT rt, List<String> textDeltas) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        if (ChatService.isEmergencyPaused()) {
            return service().sendPrivateMaintenanceMessageAsync(openId, rt);
        }
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, rt,
                C2CStreamMessage.CONTENT_TYPE_TEXT,
                C2CStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(textDeltas), null);
    }

    private static List<String> markdownTexts(List<Markdown> markdowns) {
        if (markdowns == null || markdowns.isEmpty()) {
            return List.of();
        }
        return markdowns.stream()
                .filter(Objects::nonNull)
                .map(Markdown::getText)
                .toList();
    }
}
