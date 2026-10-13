package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.internal.NoticeTools;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelNotices
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 */
public final class ChannelNotices {

    private ChannelNotices() {
    }

    /**
     * 启动 CLI 通知检查，内部 worker 参数由 CLI 管理
     *
     * @param pushCommand 推送命令模板，可为空
     * @param intervalSeconds 检查间隔秒数，必须为正整数
     * @param once 是否仅检查一次
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean notifyDaemon(String pushCommand, int intervalSeconds, boolean once) {
        return ChannelCalls.action(() -> NoticeTools.notifyDaemon(pushCommand, intervalSeconds, once, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 查看互动消息
     *
     * @param pageNum 每页数量，null 时沿用接口默认值
     * @param guildId QQ 频道 ID (可选, 筛选特定 QQ 频道)，null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getNotices(Integer pageNum, String guildId, String attachInfo) {
        return ChannelCalls.query(() -> NoticeTools.getNotices(pageNum, guildId, attachInfo, ChannelCliOptions.DEFAULT));
    }

    /**
     * 开启频道消息通知（有新互动时自动推送）
     *
     * @param sessionKey 当前会话的 sessionKey (如 agent:main:)，CLI 自动解析路由信息，null 时沿用接口默认值
     * @param confirm 确认测试推送成功，正式开启订阅，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean noticesOn(String sessionKey, Boolean confirm) {
        return ChannelCalls.action(() -> NoticeTools.noticesOn(sessionKey, confirm, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 关闭频道消息通知
     *
     * @param sessionKey 要移除的 sessionKey（仅移除该通道的推送路由，其他通道不受影响），null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean noticesOff(String sessionKey) {
        return ChannelCalls.action(() -> NoticeTools.noticesOff(sessionKey, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 查看频道消息通知状态
     *
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode noticesStatus() {
        return ChannelCalls.query(() -> NoticeTools.noticesStatus(ChannelCliOptions.DEFAULT));
    }

    /**
     * 检查新的频道通知（增量）
     *
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode checkNotices() {
        return ChannelCalls.query(() -> NoticeTools.checkNotices(ChannelCliOptions.DEFAULT));
    }

    /**
     * 开启频道消息通知
     *
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean subscribeNotices() {
        return ChannelCalls.action(() -> NoticeTools.subscribeNotices(ChannelCliOptions.CONFIRMED));
    }

    /**
     * 关闭频道消息通知
     *
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean unsubscribeNotices() {
        return ChannelCalls.action(() -> NoticeTools.unsubscribeNotices(ChannelCliOptions.CONFIRMED));
    }

    /**
     * 检查新的频道通知
     *
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode checkNewNotices() {
        return ChannelCalls.query(() -> NoticeTools.checkNewNotices(ChannelCliOptions.DEFAULT));
    }

    /**
     * 获取最近的通知记录（本地）
     *
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getRecentNotices() {
        return ChannelCalls.query(() -> NoticeTools.getRecentNotices(ChannelCliOptions.DEFAULT));
    }

    /**
     * 同意通知中的申请
     *
     * @param ref 通知编号（如 #1 中的 1），按编号查找本地通知自动填充 notice-id
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean agreeNotice(Integer ref) {
        return ChannelCalls.action(() -> NoticeTools.agreeNotice(ref, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 拒绝通知中的申请
     *
     * @param ref 通知编号（如 #1 中的 1），按编号查找本地通知自动填充 notice-id
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean refuseNotice(Integer ref) {
        return ChannelCalls.action(() -> NoticeTools.refuseNotice(ref, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 按通知 ID 同意申请
     *
     * @param noticeId 通知 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean agreeNotice(String noticeId) {
        return ChannelCalls.action(() -> NoticeTools.agreeNotice(noticeId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 按通知 ID 拒绝申请
     *
     * @param noticeId 通知 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean refuseNotice(String noticeId) {
        return ChannelCalls.action(() -> NoticeTools.refuseNotice(noticeId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 查询互动通知首页
     *
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getNotices() {
        return getNotices(null, null, null);
    }

    /**
     * 开启当前会话通知
     *
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean noticesOn() {
        return noticesOn(null, true);
    }

    /**
     * 关闭当前会话通知
     *
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean noticesOff() {
        return noticesOff(null);
    }
}
