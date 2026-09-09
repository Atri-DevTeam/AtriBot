package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelNotices
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 * @Description 频道互动消息与通知管理
 *
 * 静态同步业务入口，内部复用配置中的频道第二账号 CLI 客户端。
 * 返回原始执行结果；业务失败查看 success/getError，执行异常抛出 ChannelCliException。
 */
public final class ChannelNotices {

    private ChannelNotices() {
    }

    /**
     * 查看互动消息
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code feed.get-notices}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getNotices(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getNotices(JsonNode parameters) {
        return getNotices(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看互动消息，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 feed get-notices 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getNotices(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("feed", "get-notices", parameters, options);
    }

    /**
     * 开启频道消息通知（有新互动时自动推送）
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.notices-on}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #noticesOn(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult noticesOn(JsonNode parameters) {
        return noticesOn(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 开启频道消息通知（有新互动时自动推送），支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage notices-on 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult noticesOn(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "notices-on", parameters, options);
    }

    /**
     * 关闭频道消息通知
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.notices-off}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #noticesOff(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult noticesOff(JsonNode parameters) {
        return noticesOff(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 关闭频道消息通知，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage notices-off 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult noticesOff(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "notices-off", parameters, options);
    }

    /**
     * 查看频道消息通知状态
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.notices-status}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #noticesStatus(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult noticesStatus(JsonNode parameters) {
        return noticesStatus(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看频道消息通知状态，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage notices-status 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult noticesStatus(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "notices-status", parameters, options);
    }

    /**
     * 查看频道消息通知状态，无需参数
     *
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult noticesStatus() {
        return noticesStatus((JsonNode) null);
    }

    /**
     * 检查新的频道通知（增量）
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.check-notices}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #checkNotices(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult checkNotices(JsonNode parameters) {
        return checkNotices(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 检查新的频道通知（增量），支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage check-notices 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult checkNotices(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "check-notices", parameters, options);
    }

    /**
     * 检查新的频道通知（增量），无需参数
     *
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult checkNotices() {
        return checkNotices((JsonNode) null);
    }

    /**
     * 开启频道消息通知
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.subscribe-notices}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #subscribeNotices(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult subscribeNotices(JsonNode parameters) {
        return subscribeNotices(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 开启频道消息通知，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage subscribe-notices 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult subscribeNotices(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "subscribe-notices", parameters, options);
    }

    /**
     * 开启频道消息通知，无需参数
     *
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult subscribeNotices() {
        return subscribeNotices((JsonNode) null);
    }

    /**
     * 关闭频道消息通知
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.unsubscribe-notices}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #unsubscribeNotices(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult unsubscribeNotices(JsonNode parameters) {
        return unsubscribeNotices(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 关闭频道消息通知，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage unsubscribe-notices 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult unsubscribeNotices(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "unsubscribe-notices", parameters, options);
    }

    /**
     * 关闭频道消息通知，无需参数
     *
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult unsubscribeNotices() {
        return unsubscribeNotices((JsonNode) null);
    }

    /**
     * 检查新的频道通知
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.check-new-notices}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #checkNewNotices(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult checkNewNotices(JsonNode parameters) {
        return checkNewNotices(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 检查新的频道通知，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage check-new-notices 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult checkNewNotices(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "check-new-notices", parameters, options);
    }

    /**
     * 检查新的频道通知，无需参数
     *
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult checkNewNotices() {
        return checkNewNotices((JsonNode) null);
    }

    /**
     * 获取最近的通知记录（本地）
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-recent-notices}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getRecentNotices(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getRecentNotices(JsonNode parameters) {
        return getRecentNotices(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 获取最近的通知记录（本地），支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-recent-notices 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getRecentNotices(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-recent-notices", parameters, options);
    }

    /**
     * 获取最近的通知记录（本地），无需参数
     *
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult getRecentNotices() {
        return getRecentNotices((JsonNode) null);
    }

    /**
     * 处理系统通知（如同意/拒绝加入申请）
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.deal-notice}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #dealNotice(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult dealNotice(JsonNode parameters) {
        return dealNotice(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 处理系统通知（如同意/拒绝加入申请），支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage deal-notice 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult dealNotice(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "deal-notice", parameters, options);
    }

    /**
     * 启动后台通知检查服务
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.notify-daemon}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #notifyDaemon(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult notifyDaemon(JsonNode parameters) {
        return notifyDaemon(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 启动后台通知检查服务，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage notify-daemon 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult notifyDaemon(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "notify-daemon", parameters, options);
    }

    /**
     * 查看互动消息
     *
     * @param pageNum 每页数量，null 时沿用接口默认值
     * @param guildId 腾讯频道 ID (可选, 筛选特定腾讯频道)，null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getNotices(Integer pageNum, String guildId, String attachInfo) {
        return getNotices(pageNum, guildId, attachInfo, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看互动消息，支持确认与预演
     *
     * @param pageNum 每页数量，null 时沿用接口默认值
     * @param guildId 腾讯频道 ID (可选, 筛选特定腾讯频道)，null 时沿用接口默认值
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
        return getNotices(parameters, options);
    }

    /**
     * 开启频道消息通知（有新互动时自动推送）
     *
     * @param sessionKey 当前会话的 sessionKey (如 agent:main:)，CLI 自动解析路由信息，null 时沿用接口默认值
     * @param confirm 确认测试推送成功，正式开启订阅，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult noticesOn(String sessionKey, Boolean confirm) {
        return noticesOn(sessionKey, confirm, ChannelCliOptions.DEFAULT);
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
        return noticesOn(parameters, options);
    }

    /**
     * 关闭频道消息通知
     *
     * @param sessionKey 要移除的 sessionKey（仅移除该通道的推送路由，其他通道不受影响），null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult noticesOff(String sessionKey) {
        return noticesOff(sessionKey, ChannelCliOptions.DEFAULT);
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
        return noticesOff(parameters, options);
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
        return noticesStatus(parameters, options);
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
        return checkNotices(parameters, options);
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
        return subscribeNotices(parameters, options);
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
        return unsubscribeNotices(parameters, options);
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
        return checkNewNotices(parameters, options);
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
        return getRecentNotices(parameters, options);
    }

    /**
     * 同意通知中的申请
     *
     * @param ref 通知编号（如 #1 中的 1），按编号查找本地通知自动填充 notice-id
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult agreeNotice(Integer ref) {
        return agreeNotice(ref, ChannelCliOptions.DEFAULT);
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
        return dealNotice(parameters, options);
    }

    /**
     * 拒绝通知中的申请
     *
     * @param ref 通知编号（如 #1 中的 1），按编号查找本地通知自动填充 notice-id
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult refuseNotice(Integer ref) {
        return refuseNotice(ref, ChannelCliOptions.DEFAULT);
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
        return dealNotice(parameters, options);
    }

    /**
     * 按通知 ID 同意申请
     *
     * @param noticeId 通知 ID
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult agreeNotice(String noticeId) {
        return agreeNotice(noticeId, ChannelCliOptions.DEFAULT);
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
        return dealNotice(parameters, options);
    }

    /**
     * 按通知 ID 拒绝申请
     *
     * @param noticeId 通知 ID
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult refuseNotice(String noticeId) {
        return refuseNotice(noticeId, ChannelCliOptions.DEFAULT);
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
        return dealNotice(parameters, options);
    }

}
