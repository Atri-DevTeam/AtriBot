package top.yzljc.sakuraba_ema.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import top.yzljc.sakuraba_ema.guild.ChannelComments.FeedTarget;
import top.yzljc.sakuraba_ema.guild.ChannelComments.CommentTarget;

/**
 * @Author YZ_Ljc_
 * @ClassName CommentTools
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.internal
 * @Description 帖子评论、回复与互动
 *
 * 保留 CLI 执行选项及详细结果，供业务封装与管理指令调用
 */
public final class CommentTools {

    private CommentTools() {
    }

    private static ObjectNode feedParameters(FeedTarget target) {
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", target.guildId());
        parameters.put("channel_id", target.channelId());
        parameters.put("feed_id", target.feedId());
        parameters.put("feed_create_time", target.createTime());
        return parameters;
    }

    /**
     * 查看帖子评论，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param count 每页数量 (最大 20)，null 时沿用接口默认值
     * @param rankType 排序：0=默认，1=时间正序，2=时间倒序，null 时沿用接口默认值
     * @param replyListNum 每条评论预加载的回复数（默认1，最大10），null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getFeedComments(String guildId, String channelId, String feedId, Integer count, Integer rankType, Integer replyListNum, String attachInfo, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        if (count != null) parameters.put("count", count);
        if (rankType != null) parameters.put("rank_type", rankType);
        if (replyListNum != null) parameters.put("reply_list_num", replyListNum);
        if (attachInfo != null) parameters.put("attach_info", attachInfo);
        return ChannelCalls.client().execute("feed", "get-feed-comments", parameters, options);
    }

    /**
     * 查看更多回复（评论回复分页），支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param commentId 评论 ID
     * @param count 每页数量 (最大 50)，null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (首次从 get-feed-comments 评论对象的 attach_info 获取, 后续从本命令返回获取)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getNextPageReplies(String guildId, String channelId, String feedId, String commentId, Integer count, String attachInfo, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        if (commentId == null || commentId.isBlank()) throw new IllegalArgumentException("comment-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        parameters.put("comment_id", commentId);
        if (count != null) parameters.put("count", count);
        if (attachInfo != null) parameters.put("attach_info", attachInfo);
        return ChannelCalls.client().execute("feed", "get-next-page-replies", parameters, options);
    }

    /**
     * 按互动通知编号发表评论，支持确认与预演
     *
     * @param ref 通知编号（如 #1），按编号自动填充帖子信息
     * @param content 评论内容 (发表时与 image-path 至少填一个)
     * @param imagePath 评论图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendComment(Integer ref, String content, String imagePath, ChannelCliOptions options) {
        if (ref == null || ref < 1) throw new IllegalArgumentException("ref 必须为正整数");
        if ((content == null || content.isBlank()) && (imagePath == null || imagePath.isBlank())) {
            throw new IllegalArgumentException("正文与图片不能同时为空");
        }
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", ref);
        parameters.put("content", content);
        if (imagePath != null) parameters.put("image_path", imagePath);
        parameters.put("comment_type", 1);
        return ChannelCalls.client().execute("feed", "do-comment", parameters, options);
    }

    /**
     * 按互动通知编号回复评论，支持确认与预演
     *
     * @param ref 通知编号（如 #1），按编号自动填充帖子和评论信息
     * @param content 回复内容 (发表时与 image-path 至少填一个)
     * @param imagePath 回复图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendReply(Integer ref, String content, String imagePath, ChannelCliOptions options) {
        if (ref == null || ref < 1) throw new IllegalArgumentException("ref 必须为正整数");
        if ((content == null || content.isBlank()) && (imagePath == null || imagePath.isBlank())) {
            throw new IllegalArgumentException("正文与图片不能同时为空");
        }
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", ref);
        parameters.put("content", content);
        if (imagePath != null) parameters.put("image_path", imagePath);
        parameters.put("reply_type", 1);
        return ChannelCalls.client().execute("feed", "do-reply", parameters, options);
    }

    /**
     * 直接指定帖子发表评论，支持确认与预演
     *
     * @param target 帖子的完整定位信息
     * @param content 评论内容 (发表时与 image-path 至少填一个)
     * @param imagePath 评论图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendCommentTo(FeedTarget target, String content, String imagePath, ChannelCliOptions options) {
        if ((content == null || content.isBlank()) && (imagePath == null || imagePath.isBlank())) {
            throw new IllegalArgumentException("正文与图片不能同时为空");
        }
        java.util.Objects.requireNonNull(target, "target");
        var parameters = feedParameters(target);
        parameters.put("content", content);
        if (imagePath != null) parameters.put("image_path", imagePath);
        parameters.put("comment_type", 1);
        return ChannelCalls.client().execute("feed", "do-comment", parameters, options);
    }

    /**
     * 删除自己的评论，支持确认与预演
     *
     * @param target 帖子的完整定位信息
     * @param commentId 评论 ID (删除时必填)
     * @param commentAuthorId 评论作者 ID (删除时必填)
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteComment(FeedTarget target, String commentId, String commentAuthorId, ChannelCliOptions options) {
        if (commentId == null || commentId.isBlank()) throw new IllegalArgumentException("comment-id 不能为空");
        if (commentAuthorId == null || commentAuthorId.isBlank()) throw new IllegalArgumentException("comment-author-id 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        var parameters = feedParameters(target);
        parameters.put("comment_id", commentId);
        parameters.put("comment_author_id", commentAuthorId);
        parameters.put("comment_type", 0);
        return ChannelCalls.client().execute("feed", "do-comment", parameters, options);
    }

    /**
     * 以帖主身份删除评论，支持确认与预演
     *
     * @param target 帖子的完整定位信息
     * @param commentId 评论 ID (删除时必填)
     * @param commentAuthorId 评论作者 ID (删除时必填)
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteCommentAsOwner(FeedTarget target, String commentId, String commentAuthorId, ChannelCliOptions options) {
        if (commentId == null || commentId.isBlank()) throw new IllegalArgumentException("comment-id 不能为空");
        if (commentAuthorId == null || commentAuthorId.isBlank()) throw new IllegalArgumentException("comment-author-id 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        var parameters = feedParameters(target);
        parameters.put("comment_id", commentId);
        parameters.put("comment_author_id", commentAuthorId);
        parameters.put("comment_type", 2);
        return ChannelCalls.client().execute("feed", "do-comment", parameters, options);
    }

    /**
     * 搜索帖子并评论，支持确认与预演
     *
     * @param guildId 频道 ID
     * @param query 搜索关键词
     * @param content 评论内容
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchAndComment(String guildId, String query, String content, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("query", query);
        parameters.put("content", content);
        return ChannelCalls.client().execute("feed", "search-and-comment", parameters, options);
    }

    /**
     * 搜索帖子并评论，支持确认与预演
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeSearchAndComment(String resumeId, String pick, ChannelCliOptions options) {
        if (resumeId == null || resumeId.isBlank()) throw new IllegalArgumentException("resume-id 不能为空");
        if (pick == null || pick.isBlank()) throw new IllegalArgumentException("pick 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("resume_id", resumeId);
        parameters.put("pick", pick);
        return ChannelCalls.client().execute("feed", "search-and-comment", parameters, options);
    }

    /**
     * 搜帖删帖并禁言，支持确认与预演
     *
     * @param guildId 频道 ID
     * @param query 搜索关键词
     * @param timeStamp 禁言时长（秒），null 时沿用接口默认值
     * @param set resume 时传入参数 (key=value)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchDeleteAndMute(String guildId, String query, String timeStamp, String set, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("query", query);
        if (timeStamp != null) parameters.put("time_stamp", timeStamp);
        if (set != null) parameters.put("set", set);
        return ChannelCalls.client().execute("feed", "delete-and-mute", parameters, options);
    }

    /**
     * 搜帖删帖并禁言，支持确认与预演
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @param timeStamp 禁言时长（秒），null 时沿用接口默认值
     * @param set resume 时传入参数 (key=value)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeDeleteAndMute(String resumeId, String pick, String timeStamp, String set, ChannelCliOptions options) {
        if (resumeId == null || resumeId.isBlank()) throw new IllegalArgumentException("resume-id 不能为空");
        if (pick == null || pick.isBlank()) throw new IllegalArgumentException("pick 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("resume_id", resumeId);
        parameters.put("pick", pick);
        if (timeStamp != null) parameters.put("time_stamp", timeStamp);
        if (set != null) parameters.put("set", set);
        return ChannelCalls.client().execute("feed", "delete-and-mute", parameters, options);
    }

    /**
     * 点赞评论，支持确认与预演
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult likeComment(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, ChannelCliOptions options) {
        if (feedAuthorId == null || feedAuthorId.isBlank()) throw new IllegalArgumentException("feed-author-id 不能为空");
        if (commentId == null || commentId.isBlank()) throw new IllegalArgumentException("comment-id 不能为空");
        if (commentAuthorId == null || commentAuthorId.isBlank()) throw new IllegalArgumentException("comment-author-id 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        var parameters = feedParameters(target);
        parameters.put("feed_author_id", feedAuthorId);
        parameters.put("comment_id", commentId);
        parameters.put("comment_author_id", commentAuthorId);
        parameters.put("like_type", 3);
        return ChannelCalls.client().execute("feed", "do-like", parameters, options);
    }

    /**
     * 取消评论点赞，支持确认与预演
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult unlikeComment(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, ChannelCliOptions options) {
        if (feedAuthorId == null || feedAuthorId.isBlank()) throw new IllegalArgumentException("feed-author-id 不能为空");
        if (commentId == null || commentId.isBlank()) throw new IllegalArgumentException("comment-id 不能为空");
        if (commentAuthorId == null || commentAuthorId.isBlank()) throw new IllegalArgumentException("comment-author-id 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        var parameters = feedParameters(target);
        parameters.put("feed_author_id", feedAuthorId);
        parameters.put("comment_id", commentId);
        parameters.put("comment_author_id", commentAuthorId);
        parameters.put("like_type", 4);
        return ChannelCalls.client().execute("feed", "do-like", parameters, options);
    }

    /**
     * 点赞回复，支持确认与预演
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @param replyId 回复 ID (点赞回复时必填)
     * @param replyAuthorId 回复作者 ID (点赞回复时必填)
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult likeReply(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId, ChannelCliOptions options) {
        if (feedAuthorId == null || feedAuthorId.isBlank()) throw new IllegalArgumentException("feed-author-id 不能为空");
        if (commentId == null || commentId.isBlank()) throw new IllegalArgumentException("comment-id 不能为空");
        if (commentAuthorId == null || commentAuthorId.isBlank()) throw new IllegalArgumentException("comment-author-id 不能为空");
        if (replyId == null || replyId.isBlank()) throw new IllegalArgumentException("reply-id 不能为空");
        if (replyAuthorId == null || replyAuthorId.isBlank()) throw new IllegalArgumentException("reply-author-id 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        var parameters = feedParameters(target);
        parameters.put("feed_author_id", feedAuthorId);
        parameters.put("comment_id", commentId);
        parameters.put("comment_author_id", commentAuthorId);
        parameters.put("reply_id", replyId);
        parameters.put("reply_author_id", replyAuthorId);
        parameters.put("like_type", 5);
        return ChannelCalls.client().execute("feed", "do-like", parameters, options);
    }

    /**
     * 取消回复点赞，支持确认与预演
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @param replyId 回复 ID (点赞回复时必填)
     * @param replyAuthorId 回复作者 ID (点赞回复时必填)
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult unlikeReply(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId, ChannelCliOptions options) {
        if (feedAuthorId == null || feedAuthorId.isBlank()) throw new IllegalArgumentException("feed-author-id 不能为空");
        if (commentId == null || commentId.isBlank()) throw new IllegalArgumentException("comment-id 不能为空");
        if (commentAuthorId == null || commentAuthorId.isBlank()) throw new IllegalArgumentException("comment-author-id 不能为空");
        if (replyId == null || replyId.isBlank()) throw new IllegalArgumentException("reply-id 不能为空");
        if (replyAuthorId == null || replyAuthorId.isBlank()) throw new IllegalArgumentException("reply-author-id 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        var parameters = feedParameters(target);
        parameters.put("feed_author_id", feedAuthorId);
        parameters.put("comment_id", commentId);
        parameters.put("comment_author_id", commentAuthorId);
        parameters.put("reply_id", replyId);
        parameters.put("reply_author_id", replyAuthorId);
        parameters.put("like_type", 6);
        return ChannelCalls.client().execute("feed", "do-like", parameters, options);
    }

    /**
     * 删除回复（本人），支持确认与预演
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replyId 回复 ID (删除时必填)
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteReply(CommentTarget target, String replyId, String replierId, ChannelCliOptions options) {
        if (replyId == null || replyId.isBlank()) throw new IllegalArgumentException("reply-id 不能为空");
        if (replierId == null || replierId.isBlank()) throw new IllegalArgumentException("replier-id 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(target.feed(), "target.feed");
        var parameters = feedParameters(target.feed());
        parameters.put("feed_author_id", target.feedAuthorId());
        parameters.put("comment_id", target.commentId());
        parameters.put("comment_author_id", target.commentAuthorId());
        parameters.put("comment_create_time", target.commentCreateTime());
        parameters.put("reply_id", replyId);
        parameters.put("replier_id", replierId);
        parameters.put("reply_type", 0);
        return ChannelCalls.client().execute("feed", "do-reply", parameters, options);
    }

    /**
     * 删除回复（帖主），支持确认与预演
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replyId 回复 ID (删除时必填)
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteReplyAsOwner(CommentTarget target, String replyId, String replierId, ChannelCliOptions options) {
        if (replyId == null || replyId.isBlank()) throw new IllegalArgumentException("reply-id 不能为空");
        if (replierId == null || replierId.isBlank()) throw new IllegalArgumentException("replier-id 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(target.feed(), "target.feed");
        var parameters = feedParameters(target.feed());
        parameters.put("feed_author_id", target.feedAuthorId());
        parameters.put("comment_id", target.commentId());
        parameters.put("comment_author_id", target.commentAuthorId());
        parameters.put("comment_create_time", target.commentCreateTime());
        parameters.put("reply_id", replyId);
        parameters.put("replier_id", replierId);
        parameters.put("reply_type", 2);
        return ChannelCalls.client().execute("feed", "do-reply", parameters, options);
    }

    /**
     * 直接指定评论回复，支持确认与预演
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @param content 回复内容 (发表时与 image-path 至少填一个)
     * @param imagePath 回复图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendReplyTo(CommentTarget target, String replierId, String content, String imagePath, ChannelCliOptions options) {
        return sendReplyTo(target, replierId, content, imagePath, null, null, null, options);
    }

    /**
     * 回复评论或其中的指定回复
     *
     * @param target 评论定位信息
     * @param replierId 当前回复者的 Tiny ID
     * @param content 回复正文，与图片至少提供一项
     * @param imagePath 本地图片路径，可为空
     * @param targetReplyId 被回复的回复 ID，回复评论时为空
     * @param targetUserId 被回复者的 Tiny ID，与 targetReplyId 同时提供
     * @param targetUserNick 被回复者昵称，与 targetReplyId 同时提供
     * @param options CLI 执行选项
     * @return CLI 执行结果
     */
    public static ChannelCliResult sendReplyTo(CommentTarget target, String replierId, String content, String imagePath,
                                               String targetReplyId, String targetUserId, String targetUserNick,
                                               ChannelCliOptions options) {
        if (replierId == null || replierId.isBlank()) throw new IllegalArgumentException("replier-id 不能为空");
        if ((content == null || content.isBlank()) && (imagePath == null || imagePath.isBlank())) {
            throw new IllegalArgumentException("正文与图片不能同时为空");
        }
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(target.feed(), "target.feed");
        var parameters = feedParameters(target.feed());
        parameters.put("feed_author_id", target.feedAuthorId());
        parameters.put("comment_id", target.commentId());
        parameters.put("comment_author_id", target.commentAuthorId());
        parameters.put("comment_create_time", target.commentCreateTime());
        parameters.put("replier_id", replierId);
        parameters.put("content", content);
        if (imagePath != null) parameters.put("image_path", imagePath);
        if (targetReplyId != null || targetUserId != null || targetUserNick != null) {
            if (java.util.stream.Stream.of(targetReplyId, targetUserId, targetUserNick)
                    .anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("回复目标的 ID、用户 ID 和昵称必须同时提供");
            }
            parameters.put("target_reply_id", targetReplyId);
            parameters.put("target_user_id", targetUserId);
            parameters.put("target_user_nick", targetUserNick);
        }
        parameters.put("reply_type", 1);
        return ChannelCalls.client().execute("feed", "do-reply", parameters, options);
    }
}
