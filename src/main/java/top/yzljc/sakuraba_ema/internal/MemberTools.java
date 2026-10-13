package top.yzljc.sakuraba_ema.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.time.Duration;
import java.time.Instant;

/**
 * @Author YZ_Ljc_
 * @ClassName MemberTools
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.internal
 * @Description 频道成员查询、踢出与禁言
 *
 * 保留 CLI 执行选项及详细结果，供业务封装与管理指令调用
 */
public final class MemberTools {

    /**
     * 查询当前登录账号资料
     *
     * @param guildId 频道 ID，可为空；指定时返回对应频道的账号资料
     * @param options CLI 执行选项
     * @return CLI 执行结果
     */
    public static ChannelCliResult getCurrentUser(String guildId, ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null && !guildId.isBlank()) parameters.put("guild_id", guildId);
        return ChannelCalls.client().execute("manage", "get-user-info", parameters, options);
    }

    /**
     * 设置成员禁言截止时间
     *
     * @param guildId 频道 ID
     * @param tinyId 成员 Tiny ID
     * @param expiresAt 禁言截止时间，null 表示解除禁言
     * @param options CLI 执行选项
     * @return CLI 执行结果
     */
    public static ChannelCliResult setMuteUntil(String guildId, String tinyId, java.time.Instant expiresAt, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guildId 不能为空");
        if (tinyId == null || tinyId.isBlank()) throw new IllegalArgumentException("tinyId 不能为空");
        if (expiresAt != null && expiresAt.getEpochSecond() <= 0) throw new IllegalArgumentException("截止时间必须晚于 Unix 时间起点");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("tiny_id", tinyId);
        parameters.put("time_stamp", expiresAt == null ? "0" : String.valueOf(expiresAt.getEpochSecond()));
        return ChannelCalls.client().execute("manage", "modify-member-shut-up", parameters, options);
    }

    private MemberTools() {
    }

    /**
     * 查看用户资料，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param tinyId 成员 Tiny ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getUserInfo(String guildId, String tinyId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (tinyId == null || tinyId.isBlank()) throw new IllegalArgumentException("tiny-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("tiny_id", tinyId);
        return ChannelCalls.client().execute("manage", "get-user-info", parameters, options);
    }

    /**
     * 查看成员列表（分页），支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param nextPageToken 翻页令牌，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildMemberList(String guildId, String nextPageToken, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        if (nextPageToken != null) parameters.put("next_page_token", nextPageToken);
        return ChannelCalls.client().execute("manage", "get-guild-member-list", parameters, options);
    }

    /**
     * 按昵称搜索成员，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param keyword 搜索关键词
     * @param num 每页数量，null 时沿用接口默认值
     * @param nextPos 翻页位置 (从上次返回结果获取)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult guildMemberSearch(String guildId, String keyword, Integer num, String nextPos, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (keyword == null || keyword.isBlank()) throw new IllegalArgumentException("keyword 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("keyword", keyword);
        if (num != null) parameters.put("num", num);
        if (nextPos != null) parameters.put("next_pos", nextPos);
        return ChannelCalls.client().execute("manage", "guild-member-search", parameters, options);
    }

    /**
     * 踢出成员，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param memberTinyids 成员 Tiny ID 列表 (批量踢人)
     * @param blacklist 同时拉黑该成员，null 时沿用接口默认值
     * @param revokeMsgs 撤回该成员的消息: 3d|7d|15d|30d|all，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult kickMembers(String guildId, java.util.List<String> memberTinyids, Boolean blacklist, String revokeMsgs, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (memberTinyids == null || memberTinyids.isEmpty() || memberTinyids.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("member-tinyids 不能为空或包含空项");
        }
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        var memberTinyidsArray = parameters.putArray("member_tinyids");
        memberTinyids.forEach(memberTinyidsArray::add);
        if (blacklist != null) parameters.put("blacklist", blacklist);
        if (revokeMsgs != null) parameters.put("revoke_msgs", revokeMsgs);
        return ChannelCalls.client().execute("manage", "kick-guild-member", parameters, options);
    }

    /**
     * 禁言/解禁成员，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param tinyId 成员 Tiny ID
     * @param duration 禁言时长，从当前时间起算，至少一秒
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult muteMember(String guildId, String tinyId, java.time.Duration duration, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (tinyId == null || tinyId.isBlank()) throw new IllegalArgumentException("tiny-id 不能为空");
        if (duration == null || duration.getSeconds() < 1) throw new IllegalArgumentException("禁言时长必须至少为一秒");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("tiny_id", tinyId);
        parameters.put("time_stamp", String.valueOf(java.time.Instant.now().plus(duration).getEpochSecond()));
        return ChannelCalls.client().execute("manage", "modify-member-shut-up", parameters, options);
    }

    /**
     * 禁言/解禁成员，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param tinyId 成员 Tiny ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult unmuteMember(String guildId, String tinyId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (tinyId == null || tinyId.isBlank()) throw new IllegalArgumentException("tiny-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("tiny_id", tinyId);
        parameters.put("time_stamp", "0");
        return ChannelCalls.client().execute("manage", "modify-member-shut-up", parameters, options);
    }
}
