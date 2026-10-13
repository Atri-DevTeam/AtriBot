package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.internal.CommentTools;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelComments
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 */
public final class ChannelComments {

    /**
     * 回复评论中的指定回复
     *
     * @param target 评论定位信息
     * @param replierId 当前回复者的 Tiny ID
     * @param targetReplyId 被回复的回复 ID
     * @param targetUserId 被回复者的 Tiny ID
     * @param targetUserNick 被回复者昵称
     * @param content 回复正文，与图片至少提供一项
     * @param imagePath 本地图片路径，可为空
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean replyToReply(CommentTarget target, String replierId, String targetReplyId,
                                       String targetUserId, String targetUserNick, String content, String imagePath) {
        return ChannelCalls.action(() -> CommentTools.sendReplyTo(target, replierId, content, imagePath,
                targetReplyId, targetUserId, targetUserNick, ChannelCliOptions.CONFIRMED));
    }

    private ChannelComments() {
    }

    /**
     * 发表评论所需的帖子定位信息
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param createTime 帖子创建时间戳，沿用接口返回值
     */
    public record FeedTarget(String guildId, String channelId, String feedId, String createTime) {
        public FeedTarget {
            if (java.util.stream.Stream.of(guildId, channelId, feedId, createTime)
                    .anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("帖子定位需要频道、版块、帖子 ID 和创建时间");
            }
        }
    }

    /**
     * 回复评论所需的定位信息
     *
     * @param feed 帖子定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @param commentCreateTime 评论创建时间戳，沿用接口返回值
     */
    public record CommentTarget(FeedTarget feed, String feedAuthorId, String commentId,
                                String commentAuthorId, String commentCreateTime) {
        public CommentTarget {
            if (feed == null || java.util.stream.Stream.of(feedAuthorId, commentId, commentAuthorId, commentCreateTime)
                    .anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("评论定位需要帖子、双方作者、评论 ID 和创建时间");
            }
        }
    }

    /**
     * 查看帖子评论
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param count 每页数量 (最大 20)，null 时沿用接口默认值
     * @param rankType 排序：0=默认，1=时间正序，2=时间倒序，null 时沿用接口默认值
     * @param replyListNum 每条评论预加载的回复数（默认1，最大10），null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getFeedComments(String guildId, String channelId, String feedId, Integer count, Integer rankType, Integer replyListNum, String attachInfo) {
        return ChannelCalls.query(() -> CommentTools.getFeedComments(guildId, channelId, feedId, count, rankType, replyListNum, attachInfo, ChannelCliOptions.DEFAULT));
    }

    /**
     * 查看更多回复（评论回复分页）
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param commentId 评论 ID
     * @param count 每页数量 (最大 50)，null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (首次从 get-feed-comments 评论对象的 attach_info 获取, 后续从本命令返回获取)，null 时沿用接口默认值
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getNextPageReplies(String guildId, String channelId, String feedId, String commentId, Integer count, String attachInfo) {
        return ChannelCalls.query(() -> CommentTools.getNextPageReplies(guildId, channelId, feedId, commentId, count, attachInfo, ChannelCliOptions.DEFAULT));
    }

    /**
     * 按互动通知编号发表评论
     *
     * @param ref 通知编号（如 #1），按编号自动填充帖子信息
     * @param content 评论内容 (发表时与 image-path 至少填一个)
     * @param imagePath 评论图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendComment(Integer ref, String content, String imagePath) {
        return ChannelCalls.action(() -> CommentTools.sendComment(ref, content, imagePath, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 按互动通知编号回复评论
     *
     * @param ref 通知编号（如 #1），按编号自动填充帖子和评论信息
     * @param content 回复内容 (发表时与 image-path 至少填一个)
     * @param imagePath 回复图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendReply(Integer ref, String content, String imagePath) {
        return ChannelCalls.action(() -> CommentTools.sendReply(ref, content, imagePath, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 直接指定帖子发表评论
     *
     * @param target 帖子的完整定位信息
     * @param content 评论内容 (发表时与 image-path 至少填一个)
     * @param imagePath 评论图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendCommentTo(FeedTarget target, String content, String imagePath) {
        return ChannelCalls.action(() -> CommentTools.sendCommentTo(target, content, imagePath, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 删除自己的评论
     *
     * @param target 帖子的完整定位信息
     * @param commentId 评论 ID (删除时必填)
     * @param commentAuthorId 评论作者 ID (删除时必填)
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean deleteComment(FeedTarget target, String commentId, String commentAuthorId) {
        return ChannelCalls.action(() -> CommentTools.deleteComment(target, commentId, commentAuthorId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 以帖主身份删除评论
     *
     * @param target 帖子的完整定位信息
     * @param commentId 评论 ID (删除时必填)
     * @param commentAuthorId 评论作者 ID (删除时必填)
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean deleteCommentAsOwner(FeedTarget target, String commentId, String commentAuthorId) {
        return ChannelCalls.action(() -> CommentTools.deleteCommentAsOwner(target, commentId, commentAuthorId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 搜索帖子并评论
     *
     * @param guildId 频道 ID
     * @param query 搜索关键词
     * @param content 评论内容
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode searchAndComment(String guildId, String query, String content) {
        return ChannelCalls.query(() -> CommentTools.searchAndComment(guildId, query, content, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 搜索帖子并评论
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode resumeSearchAndComment(String resumeId, String pick) {
        return ChannelCalls.query(() -> CommentTools.resumeSearchAndComment(resumeId, pick, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 搜帖删帖并禁言
     *
     * @param guildId 频道 ID
     * @param query 搜索关键词
     * @param timeStamp 禁言时长（秒），null 时沿用接口默认值
     * @param set resume 时传入参数 (key=value)，null 时沿用接口默认值
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode searchDeleteAndMute(String guildId, String query, String timeStamp, String set) {
        return ChannelCalls.query(() -> CommentTools.searchDeleteAndMute(guildId, query, timeStamp, set, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 搜帖删帖并禁言
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @param timeStamp 禁言时长（秒），null 时沿用接口默认值
     * @param set resume 时传入参数 (key=value)，null 时沿用接口默认值
     * @return 流程数据，包含候选项或后续选择标识；调用失败时为 null
     */
    public static JsonNode resumeDeleteAndMute(String resumeId, String pick, String timeStamp, String set) {
        return ChannelCalls.query(() -> CommentTools.resumeDeleteAndMute(resumeId, pick, timeStamp, set, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 点赞评论
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean likeComment(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId) {
        return ChannelCalls.action(() -> CommentTools.likeComment(target, feedAuthorId, commentId, commentAuthorId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 取消评论点赞
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean unlikeComment(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId) {
        return ChannelCalls.action(() -> CommentTools.unlikeComment(target, feedAuthorId, commentId, commentAuthorId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 点赞回复
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @param replyId 回复 ID (点赞回复时必填)
     * @param replyAuthorId 回复作者 ID (点赞回复时必填)
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean likeReply(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId) {
        return ChannelCalls.action(() -> CommentTools.likeReply(target, feedAuthorId, commentId, commentAuthorId, replyId, replyAuthorId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 取消回复点赞
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @param replyId 回复 ID (点赞回复时必填)
     * @param replyAuthorId 回复作者 ID (点赞回复时必填)
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean unlikeReply(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId) {
        return ChannelCalls.action(() -> CommentTools.unlikeReply(target, feedAuthorId, commentId, commentAuthorId, replyId, replyAuthorId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 删除回复（本人）
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replyId 回复 ID (删除时必填)
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean deleteReply(CommentTarget target, String replyId, String replierId) {
        return ChannelCalls.action(() -> CommentTools.deleteReply(target, replyId, replierId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 删除回复（帖主）
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replyId 回复 ID (删除时必填)
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean deleteReplyAsOwner(CommentTarget target, String replyId, String replierId) {
        return ChannelCalls.action(() -> CommentTools.deleteReplyAsOwner(target, replyId, replierId, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 直接指定评论回复
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @param content 回复内容 (发表时与 image-path 至少填一个)
     * @param imagePath 回复图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendReplyTo(CommentTarget target, String replierId, String content, String imagePath) {
        return ChannelCalls.action(() -> CommentTools.sendReplyTo(target, replierId, content, imagePath, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 查询帖子的评论首页
     *
     * @param guildId 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @return 业务数据，调用失败时为 null
     */
    public static JsonNode getFeedComments(String guildId, String channelId, String feedId) {
        return getFeedComments(guildId, channelId, feedId, null, null, null, null);
    }

    /**
     * 发表评论
     *
     * @param target 帖子定位信息
     * @param text 正文内容
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendComment(FeedTarget target, String text) {
        return sendCommentTo(target, text, null);
    }

    /**
     * 发表带本地图片的评论
     *
     * @param target 帖子定位信息
     * @param text 正文内容
     * @param imagePath 本地图片路径，可为空
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendComment(FeedTarget target, String text, java.nio.file.Path imagePath) {
        return sendCommentTo(target, text, imagePath == null ? null : imagePath.toAbsolutePath().normalize().toString());
    }

    /**
     * 回复评论
     *
     * @param target 评论定位信息
     * @param replierId 当前回复者 Tiny ID
     * @param text 正文内容
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendReply(CommentTarget target, String replierId, String text) {
        return sendReplyTo(target, replierId, text, null);
    }

    /**
     * 回复评论并附加本地图片
     *
     * @param target 评论定位信息
     * @param replierId 当前回复者 Tiny ID
     * @param text 正文内容
     * @param imagePath 本地图片路径，可为空
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendReply(CommentTarget target, String replierId, String text, java.nio.file.Path imagePath) {
        return sendReplyTo(target, replierId, text, imagePath == null ? null : imagePath.toAbsolutePath().normalize().toString());
    }

    /**
     * 按互动通知编号发表评论
     *
     * @param noticeRef 互动通知编号
     * @param text 正文内容
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendComment(int noticeRef, String text) {
        return sendComment(noticeRef, text, null);
    }

    /**
     * 按互动通知编号回复评论
     *
     * @param noticeRef 互动通知编号
     * @param text 正文内容
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean sendReply(int noticeRef, String text) {
        return sendReply(noticeRef, text, null);
    }
}
