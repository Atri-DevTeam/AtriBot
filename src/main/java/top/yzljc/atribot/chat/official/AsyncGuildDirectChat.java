package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;

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
    public static CompletableFuture<String> replyMessage(String guildId, String msgId, String text) {
        return service().sendGuildDirectMessageAsync(guildId, service().getBodyFactory().replyText(msgId, text));
    }

    /**
     * 异步发送频道私信图片被动消息
     *
     * @param guildId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param image       图片组件
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String guildId, String msgId, ImageComponent image) {
        return service().sendGuildDirectMessageAsync(guildId, service().getBodyFactory().guildImage(image.getData(), msgId, image.getText()));
    }
}