package top.yzljc.sakuraba_ema.guild.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.time.Duration;
import java.time.Instant;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelMembers
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild.management
 * @Description 频道成员查询、踢出与禁言
 *
 * 静态同步业务入口，内部复用配置中的频道第二账号 CLI 客户端。
 * 返回原始执行结果；业务失败查看 success/getError，执行异常抛出 ChannelCliException。
 */
public final class ChannelMembers {

    private ChannelMembers() {
    }

    /**
     * 禁言频道成员
     *
     * @param guildId 腾讯频道 ID
     * @param tinyId 成员 Tiny ID
     * @param duration 禁言时长，从当前时间起算，至少一秒
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws IllegalArgumentException 禁言时长不足一秒
     */
    public static ChannelCliResult muteMember(String guildId, String tinyId, Duration duration) {
        if (duration == null || duration.getSeconds() < 1) {
            throw new IllegalArgumentException("禁言时长必须至少为一秒");
        }
        String timestamp = String.valueOf(Instant.now().plus(duration).getEpochSecond());
        return setMuteExpireAt(guildId, tinyId, timestamp);
    }

    /**
     * 解除频道成员禁言
     *
     * @param guildId 腾讯频道 ID
     * @param tinyId 成员 Tiny ID
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult unmuteMember(String guildId, String tinyId) {
        return setMuteExpireAt(guildId, tinyId, "0");
    }

    /**
     * 踢出单个频道成员，可同时拉黑或撤回其消息
     *
     * @param guildId 腾讯频道 ID
     * @param tinyId 成员 Tiny ID
     * @param blacklist 是否同时拉黑
     * @param revokeMsgs 消息撤回范围：3d、7d、15d、30d、all；null 时不撤回
     * @param confirmed 是否向 CLI 传入 --yes
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult kickMember(String guildId, String tinyId, boolean blacklist,
                                              String revokeMsgs, boolean confirmed) {
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("tiny_id", tinyId);
        parameters.put("blacklist", blacklist);
        if (revokeMsgs != null) parameters.put("revoke_msgs", revokeMsgs);
        return kickGuildMember(parameters, confirmed ? ChannelCliOptions.CONFIRMED : ChannelCliOptions.DEFAULT);
    }

    private static ChannelCliResult setMuteExpireAt(String guildId, String tinyId, String timestamp) {
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("tiny_id", tinyId);
        parameters.put("time_stamp", timestamp);
        return modifyMemberShutUp(parameters);
    }

    /**
     * 查看用户资料
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-user-info}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getUserInfo(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getUserInfo(JsonNode parameters) {
        return getUserInfo(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看用户资料，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-user-info 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getUserInfo(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-user-info", parameters, options);
    }

    /**
     * 查看成员列表（分页）
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-guild-member-list}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getGuildMemberList(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getGuildMemberList(JsonNode parameters) {
        return getGuildMemberList(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看成员列表（分页），支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-guild-member-list 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getGuildMemberList(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-guild-member-list", parameters, options);
    }

    /**
     * 按昵称搜索成员
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.guild-member-search}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #guildMemberSearch(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult guildMemberSearch(JsonNode parameters) {
        return guildMemberSearch(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 按昵称搜索成员，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage guild-member-search 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult guildMemberSearch(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "guild-member-search", parameters, options);
    }

    /**
     * 踢出成员
     * 涉及删除等需 --yes 的操作时，使用带 options 的重载传入 CONFIRMED。
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.kick-guild-member}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #kickGuildMember(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult kickGuildMember(JsonNode parameters) {
        return kickGuildMember(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 踢出成员，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage kick-guild-member 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult kickGuildMember(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "kick-guild-member", parameters, options);
    }

    /**
     * 禁言/解禁成员
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.modify-member-shut-up}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #modifyMemberShutUp(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult modifyMemberShutUp(JsonNode parameters) {
        return modifyMemberShutUp(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 禁言/解禁成员，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage modify-member-shut-up 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult modifyMemberShutUp(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "modify-member-shut-up", parameters, options);
    }

    /**
     * 查看用户资料，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID，null 时省略
     * @param tinyId 成员 Tiny ID，null 时省略
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getUserInfo(String guildId, String tinyId) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (tinyId != null) parameters.put("tiny_id", tinyId);
        return getUserInfo(parameters);
    }

    /**
     * 查看成员列表（分页），使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param nextPageToken 翻页令牌，null 时省略
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildMemberList(String guildId, String nextPageToken) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (nextPageToken != null) parameters.put("next_page_token", nextPageToken);
        return getGuildMemberList(parameters);
    }

    /**
     * 按昵称搜索成员，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param keyword 搜索关键词
     * @param num 每页数量，null 时省略
     * @param nextPos 翻页位置 (从上次返回结果获取)，null 时省略
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult guildMemberSearch(String guildId, String keyword, Integer num, String nextPos) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (keyword != null) parameters.put("keyword", keyword);
        if (num != null) parameters.put("num", num);
        if (nextPos != null) parameters.put("next_pos", nextPos);
        return guildMemberSearch(parameters);
    }

    /**
     * 查看用户资料，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return getUserInfo(parameters, options);
    }

    /**
     * 查看成员列表（分页），支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return getGuildMemberList(parameters, options);
    }

    /**
     * 按昵称搜索成员，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return guildMemberSearch(parameters, options);
    }

    /**
     * 踢出成员
     *
     * @param guildId 腾讯频道 ID
     * @param memberTinyids 成员 Tiny ID 列表 (批量踢人)
     * @param blacklist 同时拉黑该成员，null 时沿用接口默认值
     * @param revokeMsgs 撤回该成员的消息: 3d|7d|15d|30d|all，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult kickMembers(String guildId, java.util.List<String> memberTinyids, Boolean blacklist, String revokeMsgs) {
        return kickMembers(guildId, memberTinyids, blacklist, revokeMsgs, ChannelCliOptions.DEFAULT);
    }

    /**
     * 踢出成员，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return kickGuildMember(parameters, options);
    }

    /**
     * 禁言/解禁成员，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return modifyMemberShutUp(parameters, options);
    }

    /**
     * 禁言/解禁成员，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return modifyMemberShutUp(parameters, options);
    }

}
