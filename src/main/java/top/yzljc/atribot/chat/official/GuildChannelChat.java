package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.ImageComponent;

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
     * @param channelId   子频道 ID
     * @param text        消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyMessage(String channelId, String msgId, String text) {
        return await(AsyncGuildChannelChat.replyMessage(channelId, msgId, text));
    }

    /**
     * 发送图片子频道被动消息
     *
     * @param channelId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param image       图片组件
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyImageMessage(String channelId, String msgId, ImageComponent image) {
        return await(AsyncGuildChannelChat.replyMessage(channelId, msgId, image));
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
