package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
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

    private static ChatService service() {
        return Atri.getInstance().getChatService();
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
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> sendMessage(String openId, ImageComponent image) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().privateFileUrl(openId), image.getType(), image.getData(), "单聊主动", null, null, image.getText()))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendPrivateMessageAsync(openId, request));
    }

    /**
     * 异步回复单聊纯文本消息
     *
     * @param openId    用户 openId
     * @param msgId     被回复的消息 ID
     * @param replyText 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, String msgId, String replyText) {
        return service().sendPrivateMessageAsync(openId, service().getBodyFactory().replyText(msgId, replyText));
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
     * @param msgId    被回复的消息 ID
     * @param markdown Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, String msgId, Markdown markdown) {
        return service().sendPrivateMessageAsync(openId,
                service().getBodyFactory().markdown(markdown.getText(), null, msgId, null));
    }

    /**
     * 异步回复带键盘的单聊 Markdown 消息
     *
     * @param openId   用户 openId
     * @param msgId    被回复的消息 ID
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, String msgId, Markdown markdown, Object keyboard) {
        return service().sendPrivateMessageAsync(openId,
                service().getBodyFactory().markdown(markdown.getText(), keyboard, msgId, null));
    }

    /**
     * 异步回复单聊图片消息
     *
     * @param openId 用户 openId
     * @param msgId  被回复的消息 ID
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String openId, String msgId, ImageComponent image) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().privateFileUrl(openId), image.getType(), image.getData(), "单聊", msgId, null, image.getText()))
                .thenCompose(request -> request == null
                        ? CompletableFuture.completedFuture(null)
                        : service().sendPrivateMessageAsync(openId, request));
    }

    /**
     * 异步回复单聊事件（Markdown）
     *
     * @param openId  用户 openId
     * @param eventId 事件 ID
     * @param markdown Markdown 内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String openId, String eventId, Markdown markdown) {
        return service().sendPrivateMessageAsync(openId,
                service().getBodyFactory().markdown(markdown.getText(), null, null, eventId));
    }

    /**
     * 异步回复带键盘的单聊事件（Markdown）
     *
     * @param openId   用户 openId
     * @param eventId  事件 ID
     * @param markdown Markdown 内容
     * @param keyboard 键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String openId, String eventId, Markdown markdown, Object keyboard) {
        return service().sendPrivateMessageAsync(openId,
                service().getBodyFactory().markdown(markdown.getText(), keyboard, null, eventId));
    }

    /**
     * 异步回复单聊事件（纯文本）
     *
     * @param openId  用户 openId
     * @param eventId 事件 ID
     * @param text    文本内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String openId, String eventId, String text) {
        return service().sendPrivateMessageAsync(openId,
                service().getBodyFactory().eventText(eventId, text));
    }

    /**
     * 异步回复单聊事件（图片）
     *
     * @param openId  用户 openId
     * @param eventId 事件 ID
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static CompletableFuture<String> replyEventMessage(String openId, String eventId, ImageComponent image) {
        return ThreadManager.supplyAsync(() ->
                        service().getMediaUploader().buildImageRequest(
                                service().privateFileUrl(openId), image.getType(), image.getData(), "单聊事件", null, eventId, image.getText()))
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
                openId, null, null,
                PrivateStreamMessage.CONTENT_TYPE_MARKDOWN,
                PrivateStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(texts), null);
    }

    /**
     * 异步回复单聊 Markdown 流式消息（追加到已有消息）
     *
     * @param openId         用户 openId
     * @param msgId          被回复的消息 ID
     * @param markdownDeltas Markdown 增量列表
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyStreamDeltas(String openId, String msgId, List<Markdown> markdownDeltas) {
        if (ChatService.isEmergencyPaused()) {
            return service().sendPrivateMessageAsync(openId,
                    service().getBodyFactory().replyText(msgId, ChatService.emergencyPausedMessage()));
        }
        List<String> texts = markdownTexts(markdownDeltas);
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, msgId, null,
                PrivateStreamMessage.CONTENT_TYPE_MARKDOWN,
                PrivateStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(texts), null);
    }

    /**
     * 异步回复单聊事件 Markdown 流式消息
     *
     * @param openId         用户 openId
     * @param eventId        事件 ID
     * @param markdownDeltas Markdown 增量列表
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventStreamDeltas(String openId, String eventId, List<Markdown> markdownDeltas) {
        if (ChatService.isEmergencyPaused()) {
            return service().sendPrivateMessageAsync(openId,
                    service().getBodyFactory().eventText(eventId, ChatService.emergencyPausedMessage()));
        }
        List<String> texts = markdownTexts(markdownDeltas);
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, null, eventId,
                PrivateStreamMessage.CONTENT_TYPE_MARKDOWN,
                PrivateStreamMessage.INPUT_MODE_REPLACE,
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
                openId, null, null,
                PrivateStreamMessage.CONTENT_TYPE_TEXT,
                PrivateStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(textDeltas), null);
    }

    /**
     * 异步回复单聊纯文本流式消息
     *
     * @param openId     用户 openId
     * @param msgId      被回复的消息 ID
     * @param textDeltas 文本增量列表
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyTextStreamDeltas(String openId, String msgId, List<String> textDeltas) {
        if (ChatService.isEmergencyPaused()) {
            return service().sendPrivateMessageAsync(openId,
                    service().getBodyFactory().replyText(msgId, ChatService.emergencyPausedMessage()));
        }
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, msgId, null,
                PrivateStreamMessage.CONTENT_TYPE_TEXT,
                PrivateStreamMessage.INPUT_MODE_REPLACE,
                service().getPrivateStreamHelper().toSnapshots(textDeltas), null);
    }

    /**
     * 异步回复单聊事件纯文本流式消息
     *
     * @param openId     用户 openId
     * @param eventId    事件 ID
     * @param textDeltas 文本增量列表
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyEventTextStreamDeltas(String openId, String eventId, List<String> textDeltas) {
        if (ChatService.isEmergencyPaused()) {
            return service().sendPrivateMessageAsync(openId,
                    service().getBodyFactory().eventText(eventId, ChatService.emergencyPausedMessage()));
        }
        return service().getPrivateStreamHelper().sendBatchAsync(
                openId, null, eventId,
                PrivateStreamMessage.CONTENT_TYPE_TEXT,
                PrivateStreamMessage.INPUT_MODE_REPLACE,
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
