package top.yzljc.sakuraba_ema.guild;

import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.internal.PrivateChatTools;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelPrivateChat
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 */
public final class ChannelPrivateChat {

    private ChannelPrivateChat() {
    }

    /**
     * 向目标用户发送频道私信信息
     *
     * @param sourceGuildId 来源 QQ 频道 ID
     * @param peerTinyId 目标用户的tinyID
     * @param text 消息文本内容
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendMessage(String sourceGuildId, String peerTinyId, String text) {
        return ChannelCalls.action(() -> PrivateChatTools.sendMessage(sourceGuildId, peerTinyId, text, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 向目标用户发送频道私信信息
     *
     * @param ref 通知编号（如 #1），按编号查找本地私信通知自动填充 peer-tiny-id 和 source-guild-id
     * @param text 消息文本内容
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean replyMessage(Integer ref, String text) {
        return ChannelCalls.action(() -> PrivateChatTools.replyMessage(ref, text, ChannelCliOptions.CONFIRMED));
    }
}
