package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.internal.InformationTools;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelInformation
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 */
public final class ChannelInformation {

    private ChannelInformation() {
    }

    /**
     * 查看 QQ 频道资料
     *
     * @param guildId QQ 频道 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getGuildInfo(String guildId) {
        return ChannelCalls.query(() -> InformationTools.getGuildInfo(guildId, ChannelCliOptions.DEFAULT));
    }

    /**
     * 查看我的 QQ 频道列表
     *
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getMyJoinGuildInfo() {
        return ChannelCalls.query(() -> InformationTools.getMyJoinGuildInfo(ChannelCliOptions.DEFAULT));
    }

    /**
     * 查看版块列表
     *
     * @param guildId QQ 频道 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getGuildChannelList(String guildId) {
        return ChannelCalls.query(() -> InformationTools.getGuildChannelList(guildId, ChannelCliOptions.DEFAULT));
    }

    /**
     * 搜索 QQ 频道/帖子/作者
     *
     * @param keyword 搜索关键词
     * @param scope 搜索范围: channel|feed|author|all，null 时沿用接口默认值
     * @param nextPageToken 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode searchGuildContent(String keyword, String scope, String nextPageToken) {
        return ChannelCalls.query(() -> InformationTools.searchGuildContent(keyword, scope, nextPageToken, ChannelCliOptions.DEFAULT));
    }

    /**
     * 查看 QQ 频道加入设置
     *
     * @param guildId QQ 频道 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getJoinGuildSetting(String guildId) {
        return ChannelCalls.query(() -> InformationTools.getJoinGuildSetting(guildId, ChannelCliOptions.DEFAULT));
    }

    /**
     * 获取 QQ 频道分享短链
     *
     * @param guildId QQ 频道 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getGuildShareUrl(String guildId) {
        return ChannelCalls.query(() -> InformationTools.getGuildShareUrl(guildId, ChannelCliOptions.DEFAULT));
    }

    /**
     * 查看分享链接信息
     *
     * @param url pd.qq.com 分享链接
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getShareInfo(String url) {
        return ChannelCalls.query(() -> InformationTools.getShareInfo(url, ChannelCliOptions.DEFAULT));
    }

    /**
     * 查询当前账号加入的频道
     *
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getJoinedGuilds() {
        return getMyJoinGuildInfo();
    }

    /**
     * 查询频道板块列表
     *
     * @param guildId 频道 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getChannelList(String guildId) {
        return getGuildChannelList(guildId);
    }
}
