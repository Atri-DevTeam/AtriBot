package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
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
     * 主动发送群聊图片并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param image       图片组件
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @return 消息 ID，发送失败返回 null；普通上传失败沿用文字回退
     */
    public static String sendMessage(String groupOpenId, ImageComponent image, String refIdx) {
        return await(AsyncGroupChat.sendMessage(groupOpenId, image, refIdx));
    }

    /**
     * 主动发送带键盘的群聊 Markdown 并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param markdown    Markdown 消息内容
     * @param keyboard    键盘按钮对象，无键盘时传入 null
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static String sendMessage(String groupOpenId, Markdown markdown, Object keyboard, String refIdx) {
        return await(AsyncGroupChat.sendMessage(groupOpenId, markdown, keyboard, refIdx));
    }

    /**
     * 主动发送群聊图片并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param image       图片组件
     * @return 消息 ID，发送失败返回 null；普通上传失败沿用文字回退
     */
    public static String refMessage(String groupOpenId, String refIdx, ImageComponent image) {
        return await(AsyncGroupChat.refMessage(groupOpenId, refIdx, image));
    }

    /**
     * 主动发送群聊 Markdown并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param markdown    Markdown 消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static String refMessage(String groupOpenId, String refIdx, Markdown markdown) {
        return await(AsyncGroupChat.refMessage(groupOpenId, refIdx, markdown));
    }

    /**
     * 主动发送带键盘的群聊 Markdown 并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不提供被动回复来源
     * @param markdown    Markdown 消息内容
     * @param keyboard    键盘按钮对象，无键盘时传入 null
     * @return 消息 ID，发送失败返回 null
     */
    public static String refMessage(String groupOpenId, String refIdx, Markdown markdown, Object keyboard) {
        return await(AsyncGroupChat.refMessage(groupOpenId, refIdx, markdown, keyboard));
    }


    /**
     * 回复群聊 Markdown 消息并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象，无键盘时传入 null
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String groupOpenId, RT rt, Markdown markdown, Object keyboard, String refIdx) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, markdown, keyboard, refIdx));
    }

    /**
     * 回复群聊 Markdown 消息并引用指定消息，同时 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param userOpenId  被 @ 的用户 openId
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象，无键盘时传入 null
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown, Object keyboard, String refIdx) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, userOpenId, markdown, keyboard, refIdx));
    }

    /**
     * 回复群聊图片消息并引用指定消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image       图片组件
     * @param refIdx      被引用消息的索引 ID，null 表示不引用，不改变回复来源
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static String replyMessage(String groupOpenId, RT rt, ImageComponent image, String refIdx) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, image, refIdx));
    }


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
     * 发送群聊 Ark23 主动消息
     *
     * @param groupOpenId 群 openId
     * @param ark         Ark23 消息体
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, Ark23 ark) {
        return await(AsyncGroupChat.sendMessage(groupOpenId, ark));
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
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param replyText   回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, String replyText) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, replyText));
    }

    /**
     * 回复群聊 Ark23 消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param ark         Ark23 消息体
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, Ark23 ark) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, ark));
    }

    /**
     * 引用回复群聊纯文本消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param replyText   回复内容
     * @param refIdx      被引用消息的索引 ID
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, String replyText, String refIdx) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, replyText, refIdx));
    }

    /**
     * 回复群聊 Markdown 消息并 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param userOpenId  被 @ 的用户 openId
     * @param markdown    Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, userOpenId, markdown));
    }

    /**
     * 回复群聊 Markdown 消息（不 @）
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown    Markdown 回复内容
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, Markdown markdown) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, markdown));
    }

    /**
     * 回复带键盘的群聊 Markdown 消息并 @ 用户
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param userOpenId  被 @ 的用户 openId
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown, Object keyboard) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, userOpenId, markdown, keyboard));
    }

    /**
     * 回复带键盘的群聊 Markdown 消息（不 @）
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param markdown    Markdown 回复内容
     * @param keyboard    键盘按钮对象
     * @return 消息 ID，发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, Markdown markdown, Object keyboard) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, markdown, keyboard));
    }

    /**
     * 回复群聊图片消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image       图片组件
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, ImageComponent image) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, image));
    }

    /**
     * 回复群聊文件消息
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param fileType    文件类型
     * @param value       文件 URL
     * @return 消息 ID，上传或发送失败返回 null
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, RT rt, FileType fileType, String value) {
        return await(AsyncGroupChat.replyMessage(groupOpenId, rt, fileType, value));
    }

    /**
     * 回复群聊语音消息，上传失败时不发送文本回退
     *
     * @param groupOpenId 群 openId
     * @param rt          消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param url         语音文件 URL
     * @return 消息 ID，上传或发送失败返回 null
     */
    public static String replyAudioMessage(String groupOpenId, RT rt, String url) {
        return await(AsyncGroupChat.replyAudioMessage(groupOpenId, rt, url));
    }

    /**
     * 主动发送群聊纯文本并引用消息（不携带被动回复来源）
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
     * @return 是否撤回成功
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
            if (e.getCause() instanceof QQMessageSendException officialError) {
                throw officialError;
            }
            return null;
        }
    }
}
