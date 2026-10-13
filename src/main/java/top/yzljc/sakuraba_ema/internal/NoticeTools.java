package top.yzljc.sakuraba_ema.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

/**
 * @Author YZ_Ljc_
 * @ClassName NoticeTools
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.internal
 * @Description 频道互动消息与通知管理
 *
 * 保留 CLI 执行选项及详细结果，供业务封装与管理指令调用
 */
public final class NoticeTools {

    /**
     * 启动 CLI 通知检查，内部 worker 参数由 CLI 管理
     *
     * @param pushCommand 推送命令模板，可为空
     * @param intervalSeconds 检查间隔秒数，必须为正整数
     * @param once 是否仅检查一次
     * @param options CLI 执行选项
     * @return CLI 执行结果；常驻模式的进程行为由 CLI 决定，仍受客户端超时限制
     */
    public static ChannelCliResult notifyDaemon(String pushCommand, int intervalSeconds, boolean once, ChannelCliOptions options) {
        if (intervalSeconds < 1) throw new IllegalArgumentException("检查间隔必须为正整数");
        var parameters = JsonNodeFactory.instance.objectNode();
        if (pushCommand != null && !pushCommand.isBlank()) parameters.put("push_command", pushCommand);
        parameters.put("interval", intervalSeconds);
        parameters.put("once", once);
        return ChannelCalls.client().execute("manage", "notify-daemon", parameters, options);
    }

    private NoticeTools() {
    }

    /**
     * 查看互动消息，支持确认与预演
     *
     * @param pageNum 每页数量，null 时沿用接口默认值
     * @param guildId QQ 频道 ID (可选, 筛选特定 QQ 频道)，null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getNotices(Integer pageNum, String guildId, String attachInfo, ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (pageNum != null) parameters.put("page_num", pageNum);
        if (guildId != null) parameters.put("guild_id", guildId);
        if (attachInfo != null) parameters.put("attach_info", attachInfo);
        return ChannelCalls.client().execute("feed", "get-notices", parameters, options);
    }

    /**
     * 开启频道消息通知（有新互动时自动推送），支持确认与预演
     *
     * @param sessionKey 当前会话的 sessionKey (如 agent:main:)，CLI 自动解析路由信息，null 时沿用接口默认值
     * @param confirm 确认测试推送成功，正式开启订阅，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult noticesOn(String sessionKey, Boolean confirm, ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (sessionKey != null) parameters.put("session_key", sessionKey);
        if (confirm != null) parameters.put("confirm", confirm);
        return ChannelCalls.client().execute("manage", "notices-on", parameters, options);
    }

    /**
     * 关闭频道消息通知，支持确认与预演
     *
     * @param sessionKey 要移除的 sessionKey（仅移除该通道的推送路由，其他通道不受影响），null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult noticesOff(String sessionKey, ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (sessionKey != null) parameters.put("session_key", sessionKey);
        return ChannelCalls.client().execute("manage", "notices-off", parameters, options);
    }

    /**
     * 查看频道消息通知状态，支持确认与预演
     *
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult noticesStatus(ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        return ChannelCalls.client().execute("manage", "notices-status", parameters, options);
    }

    /**
     * 检查新的频道通知（增量），支持确认与预演
     *
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult checkNotices(ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        return ChannelCalls.client().execute("manage", "check-notices", parameters, options);
    }

    /**
     * 开启频道消息通知，支持确认与预演
     *
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult subscribeNotices(ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        return ChannelCalls.client().execute("manage", "subscribe-notices", parameters, options);
    }

    /**
     * 关闭频道消息通知，支持确认与预演
     *
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult unsubscribeNotices(ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        return ChannelCalls.client().execute("manage", "unsubscribe-notices", parameters, options);
    }

    /**
     * 检查新的频道通知，支持确认与预演
     *
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult checkNewNotices(ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        return ChannelCalls.client().execute("manage", "check-new-notices", parameters, options);
    }

    /**
     * 获取最近的通知记录（本地），支持确认与预演
     *
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getRecentNotices(ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        return ChannelCalls.client().execute("manage", "get-recent-notices", parameters, options);
    }

    /**
     * 同意通知中的申请，支持确认与预演
     *
     * @param ref 通知编号（如 #1 中的 1），按编号查找本地通知自动填充 notice-id
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult agreeNotice(Integer ref, ChannelCliOptions options) {
        if (ref == null || ref < 1) throw new IllegalArgumentException("ref 必须为正整数");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", ref);
        parameters.put("action_id", "agree");
        return ChannelCalls.client().execute("manage", "deal-notice", parameters, options);
    }

    /**
     * 拒绝通知中的申请，支持确认与预演
     *
     * @param ref 通知编号（如 #1 中的 1），按编号查找本地通知自动填充 notice-id
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult refuseNotice(Integer ref, ChannelCliOptions options) {
        if (ref == null || ref < 1) throw new IllegalArgumentException("ref 必须为正整数");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", ref);
        parameters.put("action_id", "refuse");
        return ChannelCalls.client().execute("manage", "deal-notice", parameters, options);
    }

    /**
     * 按通知 ID 同意申请，支持确认与预演
     *
     * @param noticeId 通知 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult agreeNotice(String noticeId, ChannelCliOptions options) {
        if (noticeId == null || noticeId.isBlank()) throw new IllegalArgumentException("notice-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("notice_id", noticeId);
        parameters.put("action_id", "agree");
        return ChannelCalls.client().execute("manage", "deal-notice", parameters, options);
    }

    /**
     * 按通知 ID 拒绝申请，支持确认与预演
     *
     * @param noticeId 通知 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult refuseNotice(String noticeId, ChannelCliOptions options) {
        if (noticeId == null || noticeId.isBlank()) throw new IllegalArgumentException("notice-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("notice_id", noticeId);
        parameters.put("action_id", "refuse");
        return ChannelCalls.client().execute("manage", "deal-notice", parameters, options);
    }
}
