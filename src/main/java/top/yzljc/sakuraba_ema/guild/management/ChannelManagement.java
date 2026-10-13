package top.yzljc.sakuraba_ema.guild.management;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.internal.ManagementTools;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelManagement
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild.management
 */
public final class ChannelManagement {

    private ChannelManagement() {
    }

    /**
     * 修改 QQ 频道名称/简介
     *
     * @param guildId QQ 频道 ID
     * @param guildName 新 QQ 频道名称，null 时沿用接口默认值
     * @param guildProfile 新 QQ 频道简介，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean updateGuildInfo(String guildId, String guildName, String guildProfile) {
        return ChannelCalls.action(() -> ManagementTools.updateGuildInfo(guildId, guildName, guildProfile, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 修改 QQ 频道号
     *
     * @param guildId QQ 频道 ID
     * @param guildNumber 新 QQ 频道号
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean modifyGuildNumber(String guildId, String guildNumber) {
        return ChannelCalls.action(() -> ManagementTools.modifyGuildNumber(guildId, guildNumber, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 加入 QQ 频道
     *
     * @param guildId QQ 频道 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean joinGuild(String guildId) {
        return ChannelCalls.action(() -> ManagementTools.joinGuild(guildId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 创建子版块
     *
     * @param guildId QQ 频道 ID
     * @param channelName 版块名称
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean createChannel(String guildId, String channelName) {
        return ChannelCalls.action(() -> ManagementTools.createChannel(guildId, channelName, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 删除版块
     *
     * @param guildId QQ 频道 ID
     * @param channelIds 要删除的版块 ID 列表
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean deleteChannel(String guildId, java.util.List<String> channelIds) {
        return ChannelCalls.action(() -> ManagementTools.deleteChannel(guildId, channelIds, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 修改版块名称
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param channelName 新版块名称
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean modifyChannel(String guildId, String channelId, String channelName) {
        return ChannelCalls.action(() -> ManagementTools.modifyChannel(guildId, channelId, channelName, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 修改 QQ 频道头像
     *
     * @param guildId QQ 频道 ID
     * @param imagePath 头像图片路径
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean uploadGuildAvatar(String guildId, String imagePath) {
        return ChannelCalls.action(() -> ManagementTools.uploadGuildAvatar(guildId, imagePath, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 创建频道(公开/私密)
     *
     * @param imagePath 头像图片路径
     * @param theme 主题关键词 (用于自动生成名称和简介)，null 时沿用接口默认值
     * @param guildName QQ 频道名称 (≤15字)
     * @param guildProfile QQ 频道简介 (≤300字符)，null 时沿用接口默认值
     * @param communityType QQ 频道类型: public|private|公开|私密，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean createThemePrivateGuild(String imagePath, String theme, String guildName, String guildProfile, String communityType) {
        return ChannelCalls.action(() -> ManagementTools.createThemePrivateGuild(imagePath, theme, guildName, guildProfile, communityType, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 修改 QQ 频道加入设置
     *
     * @param guildId QQ 频道 ID
     * @param joinType 加入方式
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean updateJoinGuildSetting(String guildId, String joinType) {
        return ChannelCalls.action(() -> ManagementTools.updateJoinGuildSetting(guildId, joinType, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 退出 QQ 频道
     *
     * @param guildId QQ 频道 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean leaveGuild(String guildId) {
        return ChannelCalls.action(() -> ManagementTools.leaveGuild(guildId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 搜索频道并加入
     *
     * @param keyword 搜索关键词
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode searchAndJoin(String keyword) {
        return ChannelCalls.query(() -> ManagementTools.searchAndJoin(keyword, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 搜索频道并加入
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode resumeSearchAndJoin(String resumeId, String pick) {
        return ChannelCalls.query(() -> ManagementTools.resumeSearchAndJoin(resumeId, pick, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 删除指定板块
     *
     * @param guildId 频道 ID
     * @param channelId 版块 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean deleteChannel(String guildId, String channelId) {
        return deleteChannel(guildId, java.util.Collections.singletonList(channelId));
    }
}
