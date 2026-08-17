package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.ImageComponent;

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
     * @param guildId   子频道 ID
     * @param text        消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String guildId, String msgId, String text) {
        return await(AsyncGuildDirectChat.replyMessage(guildId, msgId, text));
    }

    /**
     * 发送图片子频道被动消息
     *
     * @param guildId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param image       图片组件
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyImageMessage(String guildId, String msgId, ImageComponent image) {
        return await(AsyncGuildDirectChat.replyMessage(guildId, msgId, image));
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
