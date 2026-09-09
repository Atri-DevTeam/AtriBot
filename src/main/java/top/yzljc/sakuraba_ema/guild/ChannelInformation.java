package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelInformation
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 * @Description 频道资料、版块与分享信息查询
 *
 * 静态同步业务入口，内部复用配置中的频道第二账号 CLI 客户端。
 * 返回原始执行结果；业务失败查看 success/getError，执行异常抛出 ChannelCliException。
 */
public final class ChannelInformation {

    private ChannelInformation() {
    }

    /**
     * 查询当前配置的频道第二账号已加入的全部频道
     *
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult getJoinedGuilds() {
        return getMyJoinGuildInfo();
    }

    /**
     * 查询腾讯频道资料
     *
     * @param guildId 腾讯频道 ID
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult getGuildInfo(String guildId) {
        return getGuildInfo(guildParameters(guildId));
    }

    /**
     * 查询频道版块列表，用于选择帖子发布位置
     *
     * @param guildId 腾讯频道 ID
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult getChannelList(String guildId) {
        return getGuildChannelList(guildParameters(guildId));
    }

    private static ObjectNode guildParameters(String guildId) {
        ObjectNode parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return parameters;
    }

    /**
     * 查看腾讯频道资料
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-guild-info}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getGuildInfo(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getGuildInfo(JsonNode parameters) {
        return getGuildInfo(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看腾讯频道资料，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-guild-info 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getGuildInfo(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-guild-info", parameters, options);
    }

    /**
     * 查看我的腾讯频道列表
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-my-join-guild-info}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getMyJoinGuildInfo(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getMyJoinGuildInfo(JsonNode parameters) {
        return getMyJoinGuildInfo(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看我的腾讯频道列表，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-my-join-guild-info 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getMyJoinGuildInfo(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-my-join-guild-info", parameters, options);
    }

    /**
     * 查看我的腾讯频道列表，无需参数
     *
     * @return CLI 执行结果，success 为 false 表示失败
     */
    public static ChannelCliResult getMyJoinGuildInfo() {
        return getMyJoinGuildInfo((JsonNode) null);
    }

    /**
     * 查看版块列表
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-guild-channel-list}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getGuildChannelList(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getGuildChannelList(JsonNode parameters) {
        return getGuildChannelList(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看版块列表，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-guild-channel-list 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getGuildChannelList(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-guild-channel-list", parameters, options);
    }

    /**
     * 搜索腾讯频道/帖子/作者
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.search-guild-content}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #searchGuildContent(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult searchGuildContent(JsonNode parameters) {
        return searchGuildContent(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 搜索腾讯频道/帖子/作者，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage search-guild-content 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult searchGuildContent(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "search-guild-content", parameters, options);
    }

    /**
     * 查看腾讯频道加入设置
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-join-guild-setting}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getJoinGuildSetting(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getJoinGuildSetting(JsonNode parameters) {
        return getJoinGuildSetting(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看腾讯频道加入设置，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-join-guild-setting 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getJoinGuildSetting(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-join-guild-setting", parameters, options);
    }

    /**
     * 获取腾讯频道分享短链
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-guild-share-url}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getGuildShareUrl(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getGuildShareUrl(JsonNode parameters) {
        return getGuildShareUrl(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 获取腾讯频道分享短链，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-guild-share-url 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getGuildShareUrl(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-guild-share-url", parameters, options);
    }

    /**
     * 查看分享链接信息
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.get-share-info}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getShareInfo(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getShareInfo(JsonNode parameters) {
        return getShareInfo(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看分享链接信息，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage get-share-info 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getShareInfo(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "get-share-info", parameters, options);
    }

    /**
     * 查看腾讯频道加入设置，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getJoinGuildSetting(String guildId) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        return getJoinGuildSetting(parameters);
    }

    /**
     * 获取腾讯频道分享短链，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildShareUrl(String guildId) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        return getGuildShareUrl(parameters);
    }

    /**
     * 查看分享链接信息，使用直接参数调用
     *
     * @param url pd.qq.com 分享链接
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getShareInfo(String url) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (url != null) parameters.put("url", url);
        return getShareInfo(parameters);
    }

    /**
     * 搜索腾讯频道/帖子/作者，使用直接参数调用
     *
     * @param keyword 搜索关键词
     * @param scope 搜索范围: channel|feed|author|all，null 时省略
     * @param nextPageToken 翻页令牌 (从上次返回结果获取)，null 时省略
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchGuildContent(String keyword, String scope, String nextPageToken) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (keyword != null) parameters.put("keyword", keyword);
        if (scope != null) parameters.put("scope", scope);
        if (nextPageToken != null) parameters.put("next_page_token", nextPageToken);
        return searchGuildContent(parameters);
    }

    /**
     * 查看腾讯频道资料，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildInfo(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return getGuildInfo(parameters, options);
    }

    /**
     * 查看我的腾讯频道列表，支持确认与预演
     *
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getMyJoinGuildInfo(ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        return getMyJoinGuildInfo(parameters, options);
    }

    /**
     * 查看版块列表
     *
     * @param guildId 腾讯频道 ID
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildChannelList(String guildId) {
        return getGuildChannelList(guildId, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看版块列表，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildChannelList(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return getGuildChannelList(parameters, options);
    }

    /**
     * 搜索腾讯频道/帖子/作者，支持确认与预演
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
        return searchGuildContent(parameters, options);
    }

    /**
     * 查看腾讯频道加入设置，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getJoinGuildSetting(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return getJoinGuildSetting(parameters, options);
    }

    /**
     * 获取腾讯频道分享短链，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildShareUrl(String guildId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return getGuildShareUrl(parameters, options);
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
        return getShareInfo(parameters, options);
    }

}
