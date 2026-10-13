package top.yzljc.sakuraba_ema.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

/**
 * @Author YZ_Ljc_
 * @ClassName PrivateChatTools
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.internal
 * @Description 频道第二账号私信发送
 *
 * 保留 CLI 执行选项及详细结果，供业务封装与管理指令调用
 */
public final class PrivateChatTools {

    private PrivateChatTools() {
    }

    /**
     * 向目标用户发送频道私信信息，支持确认与预演
     *
     * @param sourceGuildId 来源 QQ 频道 ID
     * @param peerTinyId 目标用户的tinyID
     * @param text 消息文本内容
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendMessage(String sourceGuildId, String peerTinyId, String text, ChannelCliOptions options) {
        if (sourceGuildId == null || sourceGuildId.isBlank()) throw new IllegalArgumentException("source-guild-id 不能为空");
        if (peerTinyId == null || peerTinyId.isBlank()) throw new IllegalArgumentException("peer-tiny-id 不能为空");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("source_guild_id", sourceGuildId);
        parameters.put("peer_tiny_id", peerTinyId);
        parameters.put("text", text);
        return ChannelCalls.client().execute("manage", "push-group-dm-msg", parameters, options);
    }

    /**
     * 向目标用户发送频道私信信息，支持确认与预演
     *
     * @param ref 通知编号（如 #1），按编号查找本地私信通知自动填充 peer-tiny-id 和 source-guild-id
     * @param text 消息文本内容
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult replyMessage(Integer ref, String text, ChannelCliOptions options) {
        if (ref == null || ref < 1) throw new IllegalArgumentException("ref 必须为正整数");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", ref);
        parameters.put("text", text);
        return ChannelCalls.client().execute("manage", "push-group-dm-msg", parameters, options);
    }
}
