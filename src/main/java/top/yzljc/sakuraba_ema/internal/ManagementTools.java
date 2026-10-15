package top.yzljc.sakuraba_ema.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

/**
 * @Author YZ_Ljc_
 * @ClassName ManagementTools
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.internal
 * @Description 频道资料、版块与加入设置管理
 *
 * 保留 CLI 执行选项及详细结果，供业务封装与管理指令调用
 */
public final class ManagementTools {

    private ManagementTools() {
    }

    /**
     * 修改 QQ 频道名称/简介，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param guildName 新 QQ 频道名称，null 时沿用接口默认值
     * @param guildProfile 新 QQ 频道简介，null 时沿用接口默认值
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
        return ChannelCalls.client().execute("manage", "update-guild-info", parameters, options);
    }

    /**
     * 修改 QQ 频道号，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param guildNumber 新 QQ 频道号
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
        return ChannelCalls.client().execute("manage", "modify-guild-number", parameters, options);
    }

    /**
     * 加入 QQ 频道，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult joinGuild(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return ChannelCalls.client().execute("manage", "join-guild", parameters, options);
    }

    /**
     * 创建子版块，支持确认与预演
     *
     * @param guildId QQ 频道 ID
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
        return ChannelCalls.client().execute("manage", "create-channel", parameters, options);
    }

    /**
     * 删除版块，支持确认与预演
     *
     * @param guildId QQ 频道 ID
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
        return ChannelCalls.client().execute("manage", "delete-channel", parameters, options);
    }

    /**
     * 修改版块名称，支持确认与预演
     *
     * @param guildId QQ 频道 ID
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
        return ChannelCalls.client().execute("manage", "modify-channel", parameters, options);
    }

    /**
     * 修改 QQ 频道头像，支持确认与预演
     *
     * @param guildId QQ 频道 ID
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
        return ChannelCalls.client().execute("manage", "upload-guild-avatar", parameters, options);
    }

    /**
     * 创建频道(公开/私密)，支持确认与预演
     *
     * @param imagePath 头像图片路径
     * @param theme 主题关键词 (用于自动生成名称和简介)，null 时沿用接口默认值
     * @param guildName QQ 频道名称 (≤15字)
     * @param guildProfile QQ 频道简介 (≤300字符)，null 时沿用接口默认值
     * @param communityType QQ 频道类型: public|private|公开|私密，null 时沿用接口默认值
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
        return ChannelCalls.client().execute("manage", "create-theme-private-guild", parameters, options);
    }

    /**
     * 修改 QQ 频道加入设置，支持确认与预演
     *
     * @param guildId QQ 频道 ID
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
        return ChannelCalls.client().execute("manage", "update-join-guild-setting", parameters, options);
    }

    /**
     * 退出 QQ 频道，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult leaveGuild(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return ChannelCalls.client().execute("manage", "leave-guild", parameters, options);
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
        return ChannelCalls.client().execute("manage", "search-and-join", parameters, options);
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
        return ChannelCalls.client().execute("manage", "search-and-join", parameters, options);
    }
}
