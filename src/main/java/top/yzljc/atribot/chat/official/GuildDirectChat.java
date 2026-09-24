package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.ImageComponent;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @Author YZ_Ljc_
 * @ClassName GuildDirectChat
 * @Created_at 2026/08/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat
 */
public final class GuildDirectChat {

    /**
     * 发送频道私信纯文本被动消息
     *
     * @param guildId 子频道 ID
     * @param rt      消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param text    消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String guildId, RT rt, String text) {
        return await(AsyncGuildDirectChat.replyMessage(guildId, rt, text));
    }

    /**
     * 发送图片子频道被动消息
     *
     * @param guildId 子频道 ID
     * @param rt      消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image   图片组件
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String guildId, RT rt, ImageComponent image) {
        return await(AsyncGuildDirectChat.replyMessage(guildId, rt, image));
    }

    /**
     * 发送频道私信 Embed 被动消息
     *
     * @param guildId 私聊状态下获取到的频道 ID
     * @param rt      消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param embed   Embed 消息
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String guildId, RT rt, Embed embed) {
        return await(AsyncGuildDirectChat.replyMessage(guildId, rt, embed));
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
