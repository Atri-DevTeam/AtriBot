package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.official.media.ImageType;

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
        return await(AsyncGuildChannelChat.replyChannelTextMessage(channelId, msgId, text));
    }

    /**
     * 发送图片子频道被动消息
     *
     * @param channelId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param imageUrl 图片 URL
     * @param type 图片类型，此处占位，只能为 {@code ImageType.URL}
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyImageMessage(String channelId, String msgId, String imageUrl, ImageType type) {
        return replyImageMessage(channelId, msgId, imageUrl, null, type);
    }

    /**
     * 发送文字子频道图片被动消息（有文字）
     *
     * @param channelId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param imageUrl 图片 URL
     * @param text   消息内容
     * @param type 图片类型，此处占位，只能为 {@code ImageType.URL}
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyImageMessage(String channelId, String msgId, String imageUrl, String text, ImageType type) {
        return await(AsyncGuildChannelChat.replyChannelImageMessage(channelId, msgId, imageUrl, text, type));
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