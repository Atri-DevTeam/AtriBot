package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;

import java.util.concurrent.CompletableFuture;

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
     * @param channelId   子频道 ID
     * @param text        消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String channelId, String msgId, String text) {
        return service().sendGuildChannelMessageAsync(channelId, service().getBodyFactory().replyText(msgId, text));
    }

    /**
     * 异步发送文字子频道图片被动消息
     *
     * @param channelId  子频道 ID
     * @param msgId   回复的消息 ID
     * @param image       图片组件
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String channelId, String msgId, ImageComponent image) {
        return service().sendGuildChannelMessageAsync(channelId, service().getBodyFactory().guildImage(image.getData(), msgId, image.getText()));
    }
}
