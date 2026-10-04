package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.thread.GuildThread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @Author YZ_Ljc_
 * @ClassName GuildChannelChat
 * @Created_at 2026/08/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 */
public final class GuildChannelChat {

    /**
     * 发送文字子频道纯文本被动消息
     *
     * @param channelId 子频道 ID
     * @param rt        消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param text      消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String channelId, RT rt, String text) {
        return await(AsyncGuildChannelChat.replyMessage(channelId, rt, text));
    }

    /**
     * 发送图片子频道被动消息
     *
     * @param channelId 子频道 ID
     * @param rt        消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image     图片组件
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String channelId, RT rt, ImageComponent image) {
        return await(AsyncGuildChannelChat.replyMessage(channelId, rt, image));
    }

    /**
     * 发送频道文字子频道 Embed 被动消息
     *
     * @param channelId 私聊状态下获取到的频道 ID
     * @param rt      消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param embed   Embed 消息
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String channelId, RT rt, Embed embed) {
        return await(AsyncGuildChannelChat.replyMessage(channelId, rt, embed));
    }

    public static String createThread(String channelId, GuildThread thread) {
        return await(AsyncGuildChannelChat.createThread(channelId, thread));
    }

    /**
     * 同步删除频道帖子，等待异步任务返回结果
     *
     * @param channelId 子频道板块 ID
     * @param threadId 帖子 ID，不是发表帖子返回的任务 ID
     * @return 平台返回成功状态时返回 true，请求失败或等待中断时返回 false
     */
    public static boolean deleteThread(String channelId, String threadId) {
        return Boolean.TRUE.equals(await(AsyncGuildChannelChat.deleteThread(channelId, threadId)));
    }

    private static <T> T await(CompletableFuture<T> future) {
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
