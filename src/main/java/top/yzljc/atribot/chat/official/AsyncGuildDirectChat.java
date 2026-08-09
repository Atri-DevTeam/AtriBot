package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.media.ImageType;

import java.util.concurrent.CompletableFuture;

/**
 * @Author YZ_Ljc_
 * @ClassName AsyncGuildDirectChat
 * @Created_at 2026/08/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 */
public final class AsyncGuildDirectChat {

    private static ChatService service() {
        return Atri.getInstance().getChatService();
    }

    /**
     * 异步发送频道私信纯文本被动消息
     *
     * @param guildId   子频道 ID
     * @param text        消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyDirectTextMessage(String guildId, String msgId, String text) {
        return service().sendGuildDirectMessageAsync(guildId, service().getBodyFactory().replyText(msgId, text));
    }

    /**
     * 异步发送频道私信图片被动消息
     *
     * @param guildId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param imageUrl 图片 URL
     * @param type 图片类型，此处占位，只能为 {@code ImageType.URL}
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyDirectImageMessage(String guildId, String msgId, String imageUrl, ImageType type) {
        return replyDirectImageMessage(guildId, msgId, imageUrl, null, type);
    }

    /**
     * 异步发送频道私信图片被动消息（有文字）
     *
     * @param guildId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param imageUrl 图片 URL
     * @param text   消息内容
     * @param type 图片类型，此处占位，只能为 {@code ImageType.URL}
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyDirectImageMessage(String guildId, String msgId, String imageUrl, String text, ImageType type) {
        return service().sendGuildDirectMessageAsync(guildId, service().getBodyFactory().guildImage(imageUrl, msgId, text));
    }
}