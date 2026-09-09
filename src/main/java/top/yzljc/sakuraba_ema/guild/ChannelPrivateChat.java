package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelPrivateChat
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 * @Description 频道第二账号私信发送
 *
 * 静态同步业务入口，内部复用配置中的频道第二账号 CLI 客户端。
 * 返回原始执行结果；业务失败查看 success/getError，执行异常抛出 ChannelCliException。
 */
public final class ChannelPrivateChat {

    private ChannelPrivateChat() {
    }

    /**
     * 向指定用户发送频道私信
     *
     * @param guildId 来源腾讯频道 ID
     * @param tinyId 接收用户 Tiny ID
     * @param text 私信正文
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult sendMessage(String guildId, String tinyId, String text) {
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("source_guild_id", guildId);
        parameters.put("peer_tiny_id", tinyId);
        parameters.put("text", text);
        return pushGroupDmMsg(parameters);
    }

    /**
     * 通过本地私信通知编号回复用户
     *
     * @param noticeRef 通知编号，如通知 #1 对应 1，由 CLI 补全来源频道与接收用户
     * @param text 回复正文
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult replyMessage(int noticeRef, String text) {
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", noticeRef);
        parameters.put("text", text);
        return pushGroupDmMsg(parameters);
    }

    /**
     * 向目标用户发送频道私信信息
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.push-group-dm-msg}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #pushGroupDmMsg(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult pushGroupDmMsg(JsonNode parameters) {
        return pushGroupDmMsg(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 向目标用户发送频道私信信息，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage push-group-dm-msg 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult pushGroupDmMsg(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "push-group-dm-msg", parameters, options);
    }

    /**
     * 向目标用户发送频道私信信息，支持确认与预演
     *
     * @param sourceGuildId 来源腾讯频道 ID
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
        return pushGroupDmMsg(parameters, options);
    }

    /**
     * 向目标用户发送频道私信信息
     *
     * @param ref 通知编号（如 #1），按编号查找本地私信通知自动填充 peer-tiny-id 和 source-guild-id
     * @param text 消息文本内容
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult replyMessage(Integer ref, String text) {
        return replyMessage(ref, text, ChannelCliOptions.DEFAULT);
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
        return pushGroupDmMsg(parameters, options);
    }

}
