package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.platform.qq.FileType;

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
     * 发送单聊正在输入通知，默认持续 60 秒
     *
     * @param openId 用户 openId
     * @return 通知是否发送成功，参数无效、暂停、等待中断或发送失败时返回 false
     */
    public static boolean sendInputNotify(String openId) {
        return sendInputNotify(openId, 60);
    }

    /**
     * 发送单聊正在输入通知，不需要回复来源或引用
     *
     * @param openId      用户 openId
     * @param inputSecond 输入状态持续秒数，必须大于 0
     * @return 通知是否发送成功，参数无效、暂停、等待中断或发送失败时返回 false
     */
    public static boolean sendInputNotify(String openId, int inputSecond) {
        try {
            return AsyncC2CChat.sendInputNotify(openId, inputSecond).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    /**
     * 发送单聊纯文本召回消息，不携带回复来源或引用
     *
     * @param openId 用户 openId
     * @param text   纯文本消息内容
     * @return 消息 ID，暂停发送或发送失败返回 null
     */
    public static String wakeupMessage(String openId, String text) {
        return await(AsyncC2CChat.wakeupMessage(openId, text));
    }

    /**
     * 发送单聊 Ark23 召回消息，不携带回复来源或引用
     *
     * @param openId 用户 openId
     * @param ark    Ark23 消息内容
     * @return 消息 ID，暂停发送或发送失败返回 null
     */
    public static String wakeupMessage(String openId, Ark23 ark) {
        return await(AsyncC2CChat.wakeupMessage(openId, ark));
    }

    /**
     * 发送单聊 Markdown 召回消息，不携带回复来源或引用
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息内容
     * @return 消息 ID，暂停发送或发送失败返回 null
     */
    public static String wakeupMessage(String openId, Markdown markdown) {
        return await(AsyncC2CChat.wakeupMessage(openId, markdown));
    }

    /**
     * 发送带键盘的单聊 Markdown 召回消息，不携带回复来源或引用
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @return 消息 ID，暂停发送或发送失败返回 null
     */
    public static String wakeupMessage(String openId, Markdown markdown, Object keyboard) {
        return await(AsyncC2CChat.wakeupMessage(openId, markdown, keyboard));
    }

    /**
     * 发送单聊图片召回消息，不携带回复来源或引用
     *
     * @param openId 用户 openId
     * @param image  图片组件，支持 URL、Base64 及附带文字
     * @return 消息 ID，暂停发送或发送失败返回 null；普通上传失败沿用文字回退
     */
    public static String wakeupMessage(String openId, ImageComponent image) {
        return await(AsyncC2CChat.wakeupMessage(openId, image));
    }

    /**
     * 发送单聊富媒体召回消息，不携带回复来源或引用
     *
     * @param openId   用户 openId
     * @param fileType 富媒体类型，支持图片、视频、语音和文件
     * @param url      文件 URL
     * @return 消息 ID，暂停发送或发送失败返回 null；语音上传失败不回退为文本，其他类型沿用文字回退
     */
    public static String wakeupMessage(String openId, FileType fileType, String url) {
        return await(AsyncC2CChat.wakeupMessage(openId, fileType, url));
    }

    /**
     * 发送单聊语音召回消息，不携带回复来源或引用
     *
     * @param openId 用户 openId
     * @param url    语音文件 URL
     * @return 消息 ID，暂停发送或上传、发送失败返回 null
     */
    public static String wakeupAudioMessage(String openId, String url) {
        return await(AsyncC2CChat.wakeupAudioMessage(openId, url));
    }

    /**
     * 发送单聊纯文本流式召回消息，不携带回复来源或引用
     *
     * @param openId     用户 openId
     * @param textDeltas 文本增量列表
     * @return 最后成功发送的消息 ID，尚未发送成功时返回 null
     */
    public static String wakeupTextStreamDeltas(String openId, List<String> textDeltas) {
        return await(AsyncC2CChat.wakeupTextStreamDeltas(openId, textDeltas));
    }

    /**
     * 发送单聊 Markdown 流式召回消息，不携带回复来源或引用
     *
     * @param openId         用户 openId
     * @param markdownDeltas Markdown 增量列表
     * @return 最后成功发送的消息 ID，尚未发送成功时返回 null
     */
    public static String wakeupStreamDeltas(String openId, List<Markdown> markdownDeltas) {
        return await(AsyncC2CChat.wakeupStreamDeltas(openId, markdownDeltas));
    }


    /**
     * 主动发送单聊图片并引用指定消息
     *
     * @param openId 用户 openId
     * @param image  图片组件
     * @param refIdx 被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @return 消息 ID，发送失败返回 null；普通上传失败沿用文字回退
     */
    public static String sendMessage(String openId, ImageComponent image, String refIdx) {
        return await(AsyncC2CChat.sendMessage(openId, image, refIdx));
    }

    /**
     * 主动发送带键盘的单聊 Markdown 并引用指定消息
     *
     * @param openId   用户 openId
     * @param markdown Markdown 消息内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static String sendMessage(String openId, Markdown markdown, Object keyboard, String refIdx) {
        return await(AsyncC2CChat.sendMessage(openId, markdown, keyboard, refIdx));
    }

    /**
     * 主动发送单聊图片并引用指定消息
     *
     * @param openId 用户 openId
     * @param refIdx 被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param image  图片组件
     * @return 消息 ID，发送失败返回 null；普通上传失败沿用文字回退
     */
    public static String refMessage(String openId, String refIdx, ImageComponent image) {
        return await(AsyncC2CChat.refMessage(openId, refIdx, image));
    }

    /**
     * 主动发送单聊 Markdown并引用指定消息
     *
     * @param openId   用户 openId
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param markdown Markdown 消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static String refMessage(String openId, String refIdx, Markdown markdown) {
        return await(AsyncC2CChat.refMessage(openId, refIdx, markdown));
    }

    /**
     * 主动发送带键盘的单聊 Markdown 并引用指定消息
     *
     * @param openId   用户 openId
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param markdown Markdown 消息内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @return 消息 ID，发送失败返回 null
     */
    public static String refMessage(String openId, String refIdx, Markdown markdown, Object keyboard) {
        return await(AsyncC2CChat.refMessage(openId, refIdx, markdown, keyboard));
    }


    /**
     * 回复单聊 Markdown 消息并引用指定消息
     *
     * @param openId   用户 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @param refIdx   被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String openId, RT rt, Markdown markdown, Object keyboard, String refIdx) {
        return await(AsyncC2CChat.replyMessage(openId, rt, markdown, keyboard, refIdx));
    }

    /**
     * 回复单聊图片消息并引用指定消息
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image  图片组件
     * @param refIdx 被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static String replyMessage(String openId, RT rt, ImageComponent image, String refIdx) {
        return await(AsyncC2CChat.replyMessage(openId, rt, image, refIdx));
    }


    /**
     * 回复单聊语音消息，上传失败时不发送文本回退
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param url    语音文件 URL
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static String replyAudioMessage(String openId, RT rt, String url) {
        return await(AsyncC2CChat.replyAudioMessage(openId, rt, url));
    }

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
     * 发送单聊 Ark23 主动消息
     *
     * @param openId 用户 openId
     * @param ark    Ark23 消息体
     * @return 消息 ID，发送失败返回 null
     * @Description 公域机器人无被动 Ark 消息权限，仅能主动调用
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, Ark23 ark) {
        return await(AsyncC2CChat.sendMessage(openId, ark));
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
     * @param rt        消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param replyText 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, RT rt, String replyText) {
        return await(AsyncC2CChat.replyMessage(openId, rt, replyText));
    }

    /**
     * 回复单聊纯文本消息并引用指定消息
     *
     * @param openId    用户 openId
     * @param rt        消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param replyText 回复内容
     * @param refIdx    被引用消息的索引 ID，不改变消息或事件回复来源
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, RT rt, String replyText, String refIdx) {
        return await(AsyncC2CChat.replyMessage(openId, rt, replyText, refIdx));
    }

    /**
     * 回复单聊 Ark23 消息
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param ark    Ark23 消息体
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, RT rt, Ark23 ark) {
        return await(AsyncC2CChat.replyMessage(openId, rt, ark));
    }

    /**
     * 主动发送单聊纯文本并引用消息（不携带被动回复来源）
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
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, RT rt, Markdown markdown) {
        return await(AsyncC2CChat.replyMessage(openId, rt, markdown));
    }

    /**
     * 回复带键盘的单聊 Markdown 消息
     *
     * @param openId   用户 openId
     * @param rt       消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, RT rt, Markdown markdown, Object keyboard) {
        return await(AsyncC2CChat.replyMessage(openId, rt, markdown, keyboard));
    }

    /**
     * 回复单聊图片消息
     *
     * @param openId 用户 openId
     * @param rt     消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image  图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, RT rt, ImageComponent image) {
        return await(AsyncC2CChat.replyMessage(openId, rt, image));
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
     * 回复单聊 Markdown 流式消息（由 RT 指定触发来源）
     *
     * @param openId         用户 openId
     * @param rt             消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdownDeltas Markdown 增量列表
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyStreamDeltas(String openId, RT rt, List<Markdown> markdownDeltas) {
        return await(AsyncC2CChat.replyStreamDeltas(openId, rt, markdownDeltas));
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
     * @param rt         消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param textDeltas 文本增量列表
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyTextStreamDeltas(String openId, RT rt, List<String> textDeltas) {
        return await(AsyncC2CChat.replyTextStreamDeltas(openId, rt, textDeltas));
    }

    /**
     * 撤回单聊消息
     *
     * @param openId    用户 openId
     * @param messageId 消息 ID
     * @return 是否撤回成功
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
            if (e.getCause() instanceof QQMessageSendException officialError) {
                throw officialError;
            }
            return null;
        }
    }
}
