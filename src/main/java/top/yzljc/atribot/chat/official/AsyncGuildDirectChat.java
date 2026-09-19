package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;

import java.util.concurrent.CompletableFuture;
import java.util.Objects;

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
     * @param guildId 子频道 ID
     * @param rt      消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param text    消息内容
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String guildId, RT rt, String text) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGuildDirectMessageAsync(guildId, service().getBodyFactory().replyText(rt, text));
    }

    /**
     * 异步发送频道私信图片被动消息
     *
     * @param guildId 子频道 ID
     * @param rt      消息或事件回复来源，使用 RT.message(id) 或 RT.event(id)
     * @param image   图片组件
     * @return 消息 ID，发送失败返回 null
     */
    public static CompletableFuture<String> replyMessage(String guildId, RT rt, ImageComponent image) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return service().sendGuildDirectMessageAsync(guildId, service().getBodyFactory().guildImage(image.getData(), rt, image.getText()));
    }
}
