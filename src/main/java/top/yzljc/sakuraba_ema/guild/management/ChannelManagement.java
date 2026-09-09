package top.yzljc.sakuraba_ema.guild.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelManagement
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild.management
 * @Description 频道资料、版块与加入设置管理
 *
 * 静态同步业务入口，内部复用配置中的频道第二账号 CLI 客户端。
 * 返回原始执行结果；业务失败查看 success/getError，执行异常抛出 ChannelCliException。
 */
public final class ChannelManagement {

    private ChannelManagement() {
    }

    /**
     * 修改腾讯频道名称/简介
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.update-guild-info}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #updateGuildInfo(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult updateGuildInfo(JsonNode parameters) {
        return updateGuildInfo(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 修改腾讯频道名称/简介，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage update-guild-info 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult updateGuildInfo(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "update-guild-info", parameters, options);
    }

    /**
     * 修改腾讯频道号
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.modify-guild-number}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #modifyGuildNumber(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult modifyGuildNumber(JsonNode parameters) {
        return modifyGuildNumber(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 修改腾讯频道号，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage modify-guild-number 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult modifyGuildNumber(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "modify-guild-number", parameters, options);
    }

    /**
     * 加入腾讯频道
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.join-guild}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #joinGuild(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult joinGuild(JsonNode parameters) {
        return joinGuild(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 加入腾讯频道，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage join-guild 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult joinGuild(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "join-guild", parameters, options);
    }

    /**
     * 创建子版块
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.create-channel}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #createChannel(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult createChannel(JsonNode parameters) {
        return createChannel(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 创建子版块，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage create-channel 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult createChannel(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "create-channel", parameters, options);
    }

    /**
     * 删除版块
     * 涉及删除等需 --yes 的操作时，使用带 options 的重载传入 CONFIRMED。
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.delete-channel}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #deleteChannel(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult deleteChannel(JsonNode parameters) {
        return deleteChannel(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 删除版块，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage delete-channel 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult deleteChannel(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "delete-channel", parameters, options);
    }

    /**
     * 修改版块名称
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.modify-channel}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #modifyChannel(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult modifyChannel(JsonNode parameters) {
        return modifyChannel(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 修改版块名称，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage modify-channel 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult modifyChannel(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "modify-channel", parameters, options);
    }

    /**
     * 修改腾讯频道头像
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.upload-guild-avatar}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #uploadGuildAvatar(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult uploadGuildAvatar(JsonNode parameters) {
        return uploadGuildAvatar(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 修改腾讯频道头像，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage upload-guild-avatar 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult uploadGuildAvatar(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "upload-guild-avatar", parameters, options);
    }

    /**
     * 创建频道(公开/私密)
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.create-theme-private-guild}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #createThemePrivateGuild(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult createThemePrivateGuild(JsonNode parameters) {
        return createThemePrivateGuild(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 创建频道(公开/私密)，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage create-theme-private-guild 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult createThemePrivateGuild(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "create-theme-private-guild", parameters, options);
    }

    /**
     * 修改腾讯频道加入设置
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.update-join-guild-setting}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #updateJoinGuildSetting(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult updateJoinGuildSetting(JsonNode parameters) {
        return updateJoinGuildSetting(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 修改腾讯频道加入设置，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage update-join-guild-setting 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult updateJoinGuildSetting(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "update-join-guild-setting", parameters, options);
    }

    /**
     * 退出腾讯频道
     * 涉及删除等需 --yes 的操作时，使用带 options 的重载传入 CONFIRMED。
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.leave-guild}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #leaveGuild(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult leaveGuild(JsonNode parameters) {
        return leaveGuild(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 退出腾讯频道，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage leave-guild 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult leaveGuild(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "leave-guild", parameters, options);
    }

    /**
     * 搜索频道并加入
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.search-and-join}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #searchAndJoin(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult searchAndJoin(JsonNode parameters) {
        return searchAndJoin(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 搜索频道并加入，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage search-and-join 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult searchAndJoin(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "search-and-join", parameters, options);
    }

    /**
     * 修改腾讯频道名称/简介，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param guildName 新腾讯频道名称，null 时省略
     * @param guildProfile 新腾讯频道简介，null 时省略
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult updateGuildInfo(String guildId, String guildName, String guildProfile) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (guildName != null) parameters.put("guild_name", guildName);
        if (guildProfile != null) parameters.put("guild_profile", guildProfile);
        return updateGuildInfo(parameters);
    }

    /**
     * 修改腾讯频道号，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param guildNumber 新腾讯频道号
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult modifyGuildNumber(String guildId, String guildNumber) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (guildNumber != null) parameters.put("guild_number", guildNumber);
        return modifyGuildNumber(parameters);
    }

    /**
     * 加入腾讯频道，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult joinGuild(String guildId) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        return joinGuild(parameters);
    }

    /**
     * 创建子版块，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param channelName 版块名称
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult createChannel(String guildId, String channelName) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (channelName != null) parameters.put("channel_name", channelName);
        return createChannel(parameters);
    }

    /**
     * 删除版块，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param channelIds 要删除的版块 ID 列表
     * @param confirmed 是否向 CLI 传入 --yes
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteChannel(String guildId, List<String> channelIds, boolean confirmed) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (channelIds != null) {
            var values = parameters.putArray("channel_ids");
            channelIds.forEach(values::add);
        }
        return deleteChannel(parameters, confirmed ? ChannelCliOptions.CONFIRMED : ChannelCliOptions.DEFAULT);
    }

    /**
     * 修改版块名称，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param channelId 版块 ID
     * @param channelName 新版块名称
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult modifyChannel(String guildId, String channelId, String channelName) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (channelId != null) parameters.put("channel_id", channelId);
        if (channelName != null) parameters.put("channel_name", channelName);
        return modifyChannel(parameters);
    }

    /**
     * 修改腾讯频道头像，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param imagePath 头像图片路径
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult uploadGuildAvatar(String guildId, String imagePath) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (imagePath != null) parameters.put("image_path", imagePath);
        return uploadGuildAvatar(parameters);
    }

    /**
     * 创建频道(公开/私密)，使用直接参数调用
     *
     * @param imagePath 头像图片路径
     * @param theme 主题关键词 (用于自动生成名称和简介)，null 时省略
     * @param guildName 腾讯频道名称 (≤15字)，null 时省略
     * @param guildProfile 腾讯频道简介 (≤300字符)，null 时省略
     * @param communityType 腾讯频道类型: public|private|公开|私密，null 时省略
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult createThemePrivateGuild(String imagePath, String theme, String guildName, String guildProfile, String communityType) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (imagePath != null) parameters.put("image_path", imagePath);
        if (theme != null) parameters.put("theme", theme);
        if (guildName != null) parameters.put("guild_name", guildName);
        if (guildProfile != null) parameters.put("guild_profile", guildProfile);
        if (communityType != null) parameters.put("community_type", communityType);
        return createThemePrivateGuild(parameters);
    }

    /**
     * 修改腾讯频道加入设置，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param joinType 加入方式
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult updateJoinGuildSetting(String guildId, String joinType) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (joinType != null) parameters.put("join_type", joinType);
        return updateJoinGuildSetting(parameters);
    }

    /**
     * 退出腾讯频道，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param confirmed 是否向 CLI 传入 --yes
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult leaveGuild(String guildId, boolean confirmed) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        return leaveGuild(parameters, confirmed ? ChannelCliOptions.CONFIRMED : ChannelCliOptions.DEFAULT);
    }

    /**
     * 修改腾讯频道名称/简介，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param guildName 新腾讯频道名称，null 时沿用接口默认值
     * @param guildProfile 新腾讯频道简介，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult updateGuildInfo(String guildId, String guildName, String guildProfile, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        if (guildName != null) parameters.put("guild_name", guildName);
        if (guildProfile != null) parameters.put("guild_profile", guildProfile);
        return updateGuildInfo(parameters, options);
    }

    /**
     * 修改腾讯频道号，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param guildNumber 新腾讯频道号
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult modifyGuildNumber(String guildId, String guildNumber, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (guildNumber == null || guildNumber.isBlank()) throw new IllegalArgumentException("guild-number 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("guild_number", guildNumber);
        return modifyGuildNumber(parameters, options);
    }

    /**
     * 加入腾讯频道，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult joinGuild(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return joinGuild(parameters, options);
    }

    /**
     * 创建子版块，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param channelName 版块名称
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult createChannel(String guildId, String channelName, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelName == null || channelName.isBlank()) throw new IllegalArgumentException("channel-name 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_name", channelName);
        return createChannel(parameters, options);
    }

    /**
     * 删除版块
     *
     * @param guildId 腾讯频道 ID
     * @param channelIds 要删除的版块 ID 列表
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteChannel(String guildId, java.util.List<String> channelIds) {
        return deleteChannel(guildId, channelIds, ChannelCliOptions.DEFAULT);
    }

    /**
     * 删除版块，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param channelIds 要删除的版块 ID 列表
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteChannel(String guildId, java.util.List<String> channelIds, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelIds == null || channelIds.isEmpty() || channelIds.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("channel-ids 不能为空或包含空项");
        }
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        var channelIdsArray = parameters.putArray("channel_ids");
        channelIds.forEach(channelIdsArray::add);
        return deleteChannel(parameters, options);
    }

    /**
     * 修改版块名称，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param channelId 版块 ID
     * @param channelName 新版块名称
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult modifyChannel(String guildId, String channelId, String channelName, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (channelName == null || channelName.isBlank()) throw new IllegalArgumentException("channel-name 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("channel_name", channelName);
        return modifyChannel(parameters, options);
    }

    /**
     * 修改腾讯频道头像，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param imagePath 头像图片路径
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult uploadGuildAvatar(String guildId, String imagePath, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (imagePath == null || imagePath.isBlank()) throw new IllegalArgumentException("image-path 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("image_path", imagePath);
        return uploadGuildAvatar(parameters, options);
    }

    /**
     * 创建频道(公开/私密)，支持确认与预演
     *
     * @param imagePath 头像图片路径
     * @param theme 主题关键词 (用于自动生成名称和简介)，null 时沿用接口默认值
     * @param guildName 腾讯频道名称 (≤15字)
     * @param guildProfile 腾讯频道简介 (≤300字符)，null 时沿用接口默认值
     * @param communityType 腾讯频道类型: public|private|公开|私密，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult createThemePrivateGuild(String imagePath, String theme, String guildName, String guildProfile, String communityType, ChannelCliOptions options) {
        if (imagePath == null || imagePath.isBlank()) throw new IllegalArgumentException("image-path 不能为空");
        if (guildName == null || guildName.isBlank()) throw new IllegalArgumentException("guild-name 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("image_path", imagePath);
        if (theme != null) parameters.put("theme", theme);
        parameters.put("guild_name", guildName);
        if (guildProfile != null) parameters.put("guild_profile", guildProfile);
        if (communityType != null) parameters.put("community_type", communityType);
        return createThemePrivateGuild(parameters, options);
    }

    /**
     * 修改腾讯频道加入设置，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param joinType 加入方式
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult updateJoinGuildSetting(String guildId, String joinType, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (joinType == null || joinType.isBlank()) throw new IllegalArgumentException("join-type 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("join_type", joinType);
        return updateJoinGuildSetting(parameters, options);
    }

    /**
     * 退出腾讯频道
     *
     * @param guildId 腾讯频道 ID
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult leaveGuild(String guildId) {
        return leaveGuild(guildId, ChannelCliOptions.DEFAULT);
    }

    /**
     * 退出腾讯频道，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult leaveGuild(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return leaveGuild(parameters, options);
    }

    /**
     * 搜索频道并加入
     *
     * @param keyword 搜索关键词
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchAndJoin(String keyword) {
        return searchAndJoin(keyword, ChannelCliOptions.DEFAULT);
    }

    /**
     * 搜索频道并加入，支持确认与预演
     *
     * @param keyword 搜索关键词
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchAndJoin(String keyword, ChannelCliOptions options) {
        if (keyword == null || keyword.isBlank()) throw new IllegalArgumentException("keyword 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("keyword", keyword);
        return searchAndJoin(parameters, options);
    }

    /**
     * 搜索频道并加入
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeSearchAndJoin(String resumeId, String pick) {
        return resumeSearchAndJoin(resumeId, pick, ChannelCliOptions.DEFAULT);
    }

    /**
     * 搜索频道并加入，支持确认与预演
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeSearchAndJoin(String resumeId, String pick, ChannelCliOptions options) {
        if (resumeId == null || resumeId.isBlank()) throw new IllegalArgumentException("resume-id 不能为空");
        if (pick == null || pick.isBlank()) throw new IllegalArgumentException("pick 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("resume_id", resumeId);
        parameters.put("pick", pick);
        return searchAndJoin(parameters, options);
    }

}
