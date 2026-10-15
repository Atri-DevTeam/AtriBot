package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.internal.PostTools;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelPosts
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 */
public final class ChannelPosts {

    private ChannelPosts() {
    }

    /**
     * 发布纯文本帖子
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param text 帖子正文
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendMessage(String guildId, String channelId, String text) {
        return ChannelCalls.action(() -> PostTools.sendMessage(guildId, channelId, text, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 发布图片帖子，支持图片组件附带的正文
     * URL 图片先下载为临时文件，再由 CLI 上传，调用结束后清理临时文件。
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param image URL 类型图片组件，暂不支持其他 ImageType
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendMessage(String guildId, String channelId, ImageComponent image) {
        return ChannelCalls.action(() -> PostTools.sendMessage(guildId, channelId, image, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 发布 Markdown 帖子，自动下载正文中的配图并转换为论坛图片占位符
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param title 帖子标题
     * @param markdown Markdown 正文
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendMessage(String guildId, String channelId, String title, Markdown markdown) {
        return ChannelCalls.action(() -> PostTools.sendMessage(guildId, channelId, title, markdown, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 获取 QQ 频道主页帖子
     *
     * @param guildId QQ 频道 ID
     * @param getType 获取类型: 1=热门 2=最新，null 时沿用接口默认值
     * @param count 每页数量，null 时沿用接口默认值
     * @param feedAttachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getGuildFeeds(String guildId, Integer getType, Integer count, String feedAttachInfo) {
        return ChannelCalls.query(() -> PostTools.getGuildFeeds(guildId, getType, count, feedAttachInfo, ChannelCliOptions.DEFAULT));
    }

    /**
     * 获取版块帖子列表
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param count 每页数量，null 时沿用接口默认值
     * @param feedAttachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getChannelTimelineFeeds(String guildId, String channelId, Integer count, String feedAttachInfo) {
        return ChannelCalls.query(() -> PostTools.getChannelTimelineFeeds(guildId, channelId, count, feedAttachInfo, ChannelCliOptions.DEFAULT));
    }

    /**
     * 查看帖子详情
     *
     * @param guildId QQ 频道 ID (推荐传入)
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getPost(String guildId, String channelId, String feedId) {
        return ChannelCalls.query(() -> PostTools.getPost(guildId, channelId, feedId, ChannelCliOptions.DEFAULT));
    }

    /**
     * 搜索帖子
     *
     * @param guildId QQ 频道 ID
     * @param query 搜索关键词
     * @param nextPageCookie 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode searchGuildFeeds(String guildId, String query, String nextPageCookie) {
        return ChannelCalls.query(() -> PostTools.searchGuildFeeds(guildId, query, nextPageCookie, ChannelCliOptions.DEFAULT));
    }

    /**
     * 获取帖子分享短链
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode sharePost(String guildId, String channelId, String feedId) {
        return ChannelCalls.query(() -> PostTools.sharePost(guildId, channelId, feedId, ChannelCliOptions.DEFAULT));
    }

    /**
     * 发表帖子
     *
     * @param guildId QQ 频道 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）
     * @param channelId 版块 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）
     * @param content 帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)
     * @param title 帖子标题 (长贴必填, 有标题自动升级为长贴)，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean publishText(String guildId, String channelId, String content, String title) {
        return ChannelCalls.action(() -> PostTools.publishText(guildId, channelId, content, title, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 发表帖子
     *
     * @param guildId QQ 频道 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）
     * @param channelId 版块 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）
     * @param filePaths file-paths
     * @param content 帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)
     * @param title 帖子标题 (长贴必填, 有标题自动升级为长贴)，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean publishImages(String guildId, String channelId, java.util.List<String> filePaths, String content, String title) {
        return ChannelCalls.action(() -> PostTools.publishImages(guildId, channelId, filePaths, content, title, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 删除帖子
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param createTime 创建时间戳
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean deleteFeed(String guildId, String channelId, String feedId, String createTime) {
        return ChannelCalls.action(() -> PostTools.deleteFeed(guildId, channelId, feedId, createTime, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 点赞帖子
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean likeFeed(String guildId, String channelId, String feedId) {
        return ChannelCalls.action(() -> PostTools.likeFeed(guildId, channelId, feedId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 取消帖子点赞
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean unlikeFeed(String guildId, String channelId, String feedId) {
        return ChannelCalls.action(() -> PostTools.unlikeFeed(guildId, channelId, feedId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 编辑帖子
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param createTime 帖子创建时间戳
     * @param content 新内容（普通文本模式；与 --markdown-content 互斥）
     * @param title 新标题，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean editText(String guildId, String channelId, String feedId, String createTime, String content, String title) {
        return ChannelCalls.action(() -> PostTools.editText(guildId, channelId, feedId, createTime, content, title, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 编辑帖子
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param createTime 帖子创建时间戳
     * @param markdownContent Markdown 正文（仅长贴; 短贴自动降级; 与 --content 互斥）
     * @param title 新标题，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean editMarkdown(String guildId, String channelId, String feedId, String createTime, String markdownContent, String title) {
        return ChannelCalls.action(() -> PostTools.editMarkdown(guildId, channelId, feedId, createTime, markdownContent, title, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 置顶帖子
     *
     * @param guildId QQ 频道 ID
     * @param feedId 帖子 ID
     * @param userId 帖子发表者用户 ID
     * @param createTime 帖子创建时间戳
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean pinFeed(String guildId, String feedId, String userId, String createTime) {
        return ChannelCalls.action(() -> PostTools.pinFeed(guildId, feedId, userId, createTime, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 取消置顶帖子
     *
     * @param guildId QQ 频道 ID
     * @param feedId 帖子 ID
     * @param userId 帖子发表者用户 ID
     * @param createTime 帖子创建时间戳
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean unpinFeed(String guildId, String feedId, String userId, String createTime) {
        return ChannelCalls.action(() -> PostTools.unpinFeed(guildId, feedId, userId, createTime, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 设置精华帖子
     *
     * @param feedId 帖子 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean markEssence(String feedId) {
        return ChannelCalls.action(() -> PostTools.markEssence(feedId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 取消精华帖子
     *
     * @param feedId 帖子 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean removeEssence(String feedId) {
        return ChannelCalls.action(() -> PostTools.removeEssence(feedId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 推送精华帖通知
     *
     * @param feedId 精华帖 ID (需先设置为精华)
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean pushEssenceFeed(String feedId) {
        return ChannelCalls.action(() -> PostTools.pushEssenceFeed(feedId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 移动帖子到其他版块
     *
     * @param guildId 频道 ID
     * @param originalChannelId 帖子当前所在版块 ID
     * @param channelId 目标版块 ID
     * @param feedId 帖子 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean movePost(String guildId, String originalChannelId, String channelId, String feedId) {
        return ChannelCalls.action(() -> PostTools.movePost(guildId, originalChannelId, channelId, feedId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 选择频道和版块，一键发帖
     *
     * @param content 帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)
     * @param title 帖子标题 (有标题自动升级为长贴)，null 时沿用接口默认值
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode quickPublishText(String content, String title) {
        return ChannelCalls.query(() -> PostTools.quickPublishText(content, title, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 选择频道和版块，一键发帖
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode resumeQuickPublish(String resumeId, String pick) {
        return ChannelCalls.query(() -> PostTools.resumeQuickPublish(resumeId, pick, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 获取频道最新帖子详情
     *
     * @param count 帖子数量，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode latestFeedsDetail(Integer count) {
        return ChannelCalls.query(() -> PostTools.latestFeedsDetail(count, ChannelCliOptions.DEFAULT));
    }

    /**
     * 获取频道最新帖子详情
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode resumeLatestFeedsDetail(String resumeId, String pick) {
        return ChannelCalls.query(() -> PostTools.resumeLatestFeedsDetail(resumeId, pick, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 获取频道热门帖子详情
     *
     * @param count 帖子数量，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode hotFeedsDetail(Integer count) {
        return ChannelCalls.query(() -> PostTools.hotFeedsDetail(count, ChannelCliOptions.DEFAULT));
    }

    /**
     * 获取频道热门帖子详情
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode resumeHotFeedsDetail(String resumeId, String pick) {
        return ChannelCalls.query(() -> PostTools.resumeHotFeedsDetail(resumeId, pick, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 查询频道主页帖子首页
     *
     * @param guildId 频道 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getGuildFeeds(String guildId) {
        return getGuildFeeds(guildId, null, null, null);
    }

    /**
     * 查询板块帖子首页
     *
     * @param guildId 频道 ID
     * @param channelId 版块 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getChannelTimelineFeeds(String guildId, String channelId) {
        return getChannelTimelineFeeds(guildId, channelId, null, null);
    }

    /**
     * 搜索频道帖子
     *
     * @param guildId 频道 ID
     * @param query 搜索关键词
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode searchGuildFeeds(String guildId, String query) {
        return searchGuildFeeds(guildId, query, null);
    }
}
