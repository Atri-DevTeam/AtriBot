package top.yzljc.sakuraba_ema.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

/**
 * @Author YZ_Ljc_
 * @ClassName InformationTools
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.internal
 * @Description 频道资料、版块与分享信息查询
 *
 * 保留 CLI 执行选项及详细结果，供业务封装与管理指令调用
 */
public final class InformationTools {

    private InformationTools() {
    }

    private static ObjectNode guildParameters(String guildId) {
        ObjectNode parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return parameters;
    }

    /**
     * 查看 QQ 频道资料，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildInfo(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return ChannelCalls.client().execute("manage", "get-guild-info", parameters, options);
    }

    /**
     * 查看我的 QQ 频道列表，支持确认与预演
     *
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getMyJoinGuildInfo(ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        return ChannelCalls.client().execute("manage", "get-my-join-guild-info", parameters, options);
    }

    /**
     * 查看版块列表，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildChannelList(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return ChannelCalls.client().execute("manage", "get-guild-channel-list", parameters, options);
    }

    /**
     * 搜索 QQ 频道/帖子/作者，支持确认与预演
     *
     * @param keyword 搜索关键词
     * @param scope 搜索范围: channel|feed|author|all，null 时沿用接口默认值
     * @param nextPageToken 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchGuildContent(String keyword, String scope, String nextPageToken, ChannelCliOptions options) {
        if (keyword == null || keyword.isBlank()) throw new IllegalArgumentException("keyword 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("keyword", keyword);
        if (scope != null) parameters.put("scope", scope);
        if (nextPageToken != null) parameters.put("next_page_token", nextPageToken);
        return ChannelCalls.client().execute("manage", "search-guild-content", parameters, options);
    }

    /**
     * 查看 QQ 频道加入设置，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getJoinGuildSetting(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return ChannelCalls.client().execute("manage", "get-join-guild-setting", parameters, options);
    }

    /**
     * 获取 QQ 频道分享短链，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildShareUrl(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return ChannelCalls.client().execute("manage", "get-guild-share-url", parameters, options);
    }

    /**
     * 查看分享链接信息，支持确认与预演
     *
     * @param url pd.qq.com 分享链接
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getShareInfo(String url, ChannelCliOptions options) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("url 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("url", url);
        return ChannelCalls.client().execute("manage", "get-share-info", parameters, options);
    }
}
