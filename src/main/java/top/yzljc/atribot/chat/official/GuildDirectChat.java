package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.official.media.ImageType;

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
        return await(AsyncGuildDirectChat.replyDirectTextMessage(guildId, msgId, text));
    }

    /**
     * 发送图片子频道被动消息
     *
     * @param guildId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param imageUrl 图片 URL
     * @param type 图片类型，此处占位，只能为 {@code ImageType.URL}
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyImageMessage(String guildId, String msgId, String imageUrl, ImageType type) {
        return replyImageMessage(guildId, msgId, imageUrl, null, type);
    }

    /**
     * 发送频道私信图片被动消息（有文字）
     *
     * @param guildId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param imageUrl 图片 URL
     * @param text   消息内容
     * @param type 图片类型，此处占位，只能为 {@code ImageType.URL}
     * @return 消息 ID，发送失败返回 null
     */
    public static String replyImageMessage(String guildId, String msgId, String imageUrl, String text, ImageType type) {
        return await(AsyncGuildDirectChat.replyDirectImageMessage(guildId, msgId, imageUrl, text, type));
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