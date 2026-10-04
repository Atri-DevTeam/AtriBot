package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.thread.GuildThread;

import java.util.concurrent.CompletableFuture;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName AsyncGuildChat
 * @Created_at 2026/08/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat
 */
public final class AsyncGuildChannelChat {

    private static ChatService service() {
        return Atri.getInstance().getChatService();
    }

    /**
     * 异步发送文字子频道纯文本被动消息
     *
     * @param channelId 子频道 ID
     * @param rt        消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param text      消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String channelId, RT rt, String text) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGuildChannelMessageAsync(channelId, service().getBodyFactory().replyText(rt, text));
    }

    /**
     * 异步发送文字子频道图片被动消息
     *
     * @param channelId 子频道 ID
     * @param rt        消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image     图片组件
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String channelId, RT rt, ImageComponent image) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGuildChannelMessageAsync(channelId, service().getBodyFactory().guildImage(image.getData(), rt, image.getText()));
    }

    /**
     * 异步发送频道文字子频道 Embed 被动消息
     *
     * @param channelId 私聊状态下获取到的频道 ID
     * @param rt      消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param embed   Embed 消息
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String channelId, RT rt, Embed embed) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGuildChannelMessageAsync(channelId, service().getBodyFactory().embeds(rt, embed));
    }

    /**
     * 异步发表频道帖子到指定板块
     *
     * @param channelId 板块 ID
     * @param thread 帖子内容
     * @return 帖子任务 ID，发表失败时为 null
     */
    public static CompletableFuture<String> createThread(String channelId, GuildThread thread) {
        return service().createGuildThreadAsync(channelId, thread);
    }

    /**
     * 异步删除频道帖子
     *
     * @param channelId 子频道板块 ID
     * @param threadId 帖子 ID，不是发表帖子返回的任务 ID
     * @return 删除结果，平台返回成功状态时为 true，请求失败时为 false
     */
    public static CompletableFuture<Boolean> deleteThread(String channelId, String threadId) {
        return service().deleteGuildThreadAsync(channelId, threadId);
    }
}
