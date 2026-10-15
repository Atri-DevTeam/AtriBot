package top.yzljc.sakuraba_ema.guild.management;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.internal.MemberTools;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelMembers
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild.management
 */
public final class ChannelMembers {

    private ChannelMembers() {
    }

    /**
     * 查询当前登录账号资料
     *
     * @param guildId 频道 ID，可为空；指定时返回对应频道的账号资料
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getCurrentUser(String guildId) {
        return ChannelCalls.query(() -> MemberTools.getCurrentUser(guildId, ChannelCliOptions.DEFAULT));
    }

    /**
     * 设置成员禁言截止时间
     *
     * @param guildId 频道 ID
     * @param tinyId 成员 Tiny ID
     * @param expiresAt 禁言截止时间，null 表示解除禁言
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean setMuteUntil(String guildId, String tinyId, java.time.Instant expiresAt) {
        return ChannelCalls.action(() -> MemberTools.setMuteUntil(guildId, tinyId, expiresAt, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 查看用户资料
     *
     * @param guildId QQ 频道 ID
     * @param tinyId 成员 Tiny ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getUserInfo(String guildId, String tinyId) {
        return ChannelCalls.query(() -> MemberTools.getUserInfo(guildId, tinyId, ChannelCliOptions.DEFAULT));
    }

    /**
     * 查看成员列表（分页）
     *
     * @param guildId QQ 频道 ID
     * @param nextPageToken 翻页令牌，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getGuildMemberList(String guildId, String nextPageToken) {
        return ChannelCalls.query(() -> MemberTools.getGuildMemberList(guildId, nextPageToken, ChannelCliOptions.DEFAULT));
    }

    /**
     * 按昵称搜索成员
     *
     * @param guildId QQ 频道 ID
     * @param keyword 搜索关键词
     * @param num 每页数量，null 时沿用接口默认值
     * @param nextPos 翻页位置 (从上次返回结果获取)，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode guildMemberSearch(String guildId, String keyword, Integer num, String nextPos) {
        return ChannelCalls.query(() -> MemberTools.guildMemberSearch(guildId, keyword, num, nextPos, ChannelCliOptions.DEFAULT));
    }

    /**
     * 踢出成员
     *
     * @param guildId QQ 频道 ID
     * @param memberTinyids 成员 Tiny ID 列表 (批量踢人)
     * @param blacklist 同时拉黑该成员，null 时沿用接口默认值
     * @param revokeMsgs 撤回该成员的消息: 3d|7d|15d|30d|all，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean kickMembers(String guildId, java.util.List<String> memberTinyids, Boolean blacklist, String revokeMsgs) {
        return ChannelCalls.action(() -> MemberTools.kickMembers(guildId, memberTinyids, blacklist, revokeMsgs, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 禁言/解禁成员
     *
     * @param guildId QQ 频道 ID
     * @param tinyId 成员 Tiny ID
     * @param duration 禁言时长，从当前时间起算，至少一秒
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean muteMember(String guildId, String tinyId, java.time.Duration duration) {
        return ChannelCalls.action(() -> MemberTools.muteMember(guildId, tinyId, duration, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 禁言/解禁成员
     *
     * @param guildId QQ 频道 ID
     * @param tinyId 成员 Tiny ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean unmuteMember(String guildId, String tinyId) {
        return ChannelCalls.action(() -> MemberTools.unmuteMember(guildId, tinyId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 查询当前登录账号资料
     *
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getCurrentUser() {
        return getCurrentUser(null);
    }

    /**
     * 查询频道成员列表
     *
     * @param guildId 频道 ID
     * @param cursor 分页游标，首次查询可为空
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getMemberList(String guildId, String cursor) {
        return getGuildMemberList(guildId, cursor);
    }

    /**
     * 查询频道成员列表首页
     *
     * @param guildId 频道 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getMemberList(String guildId) {
        return getMemberList(guildId, null);
    }

    /**
     * 查询频道成员资料
     *
     * @param guildId 频道 ID
     * @param tinyId 成员 Tiny ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getMemberInfo(String guildId, String tinyId) {
        return getUserInfo(guildId, tinyId);
    }

    /**
     * 按昵称搜索频道成员
     *
     * @param guildId 频道 ID
     * @param keyword 昵称关键词
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode searchMembers(String guildId, String keyword) {
        return guildMemberSearch(guildId, keyword, null, null);
    }

    /**
     * 移除频道成员
     *
     * @param guildId 频道 ID
     * @param tinyId 成员 Tiny ID
     * @param blacklist 是否同时加入黑名单
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean kickMember(String guildId, String tinyId, boolean blacklist) {
        return kickMembers(guildId, java.util.Collections.singletonList(tinyId), blacklist, null);
    }
}
