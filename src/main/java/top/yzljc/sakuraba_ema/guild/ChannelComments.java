package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.nio.file.Path;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelComments
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild
 * @Description 帖子评论、回复与互动
 *
 * 静态同步业务入口，内部复用配置中的频道第二账号 CLI 客户端。
 * 返回原始执行结果；业务失败查看 success/getError，执行异常抛出 ChannelCliException。
 */
public final class ChannelComments {

    private ChannelComments() {
    }

    /**
     * 发表评论所需的帖子定位信息
     *
     * @param guildId 腾讯频道 ID
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
     * 发表纯文本评论
     *
     * @param target 帖子定位信息
     * @param text 评论正文，支持 CLI 内联 @ 与链接语法
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult sendComment(FeedTarget target, String text) {
        return sendComment(target, text, null);
    }

    /**
     * 发表带图片的评论，由 CLI 上传本地图片
     *
     * @param target 帖子定位信息
     * @param text 评论正文，与 imagePath 至少提供一个
     * @param imagePath 本地图片路径，最多一张；null 时仅发送文字
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult sendComment(FeedTarget target, String text, Path imagePath) {
        var parameters = feedParameters(target);
        parameters.put("comment_type", 1);
        if (text != null) parameters.put("content", text);
        if (imagePath != null) parameters.put("image_path", imagePath.toAbsolutePath().normalize().toString());
        return doComment(parameters);
    }

    /**
     * 回复指定评论
     *
     * @param target 评论及所属帖子定位信息
     * @param replierId 当前回复人的用户 ID
     * @param text 回复正文
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult sendReply(CommentTarget target, String replierId, String text) {
        return sendReply(target, replierId, text, null);
    }

    /**
     * 回复指定评论，可附带一张本地图片
     *
     * @param target 评论及所属帖子定位信息
     * @param replierId 当前回复人的用户 ID
     * @param text 回复正文，与 imagePath 至少提供一个
     * @param imagePath 本地图片路径，null 时仅发送文字
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult sendReply(CommentTarget target, String replierId, String text,
                                             Path imagePath) {
        var parameters = feedParameters(target.feed());
        parameters.put("feed_author_id", target.feedAuthorId());
        parameters.put("comment_id", target.commentId());
        parameters.put("comment_author_id", target.commentAuthorId());
        parameters.put("comment_create_time", target.commentCreateTime());
        parameters.put("replier_id", replierId);
        parameters.put("reply_type", 1);
        if (text != null) parameters.put("content", text);
        if (imagePath != null) parameters.put("image_path", imagePath.toAbsolutePath().normalize().toString());
        return doReply(parameters);
    }

    /**
     * 根据互动通知编号发表评论，由 CLI 补全帖子信息
     *
     * @param noticeRef 通知编号，如通知 #1 对应 1
     * @param text 评论正文
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult sendComment(int noticeRef, String text) {
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", noticeRef);
        parameters.put("comment_type", 1);
        parameters.put("content", text);
        return doComment(parameters);
    }

    /**
     * 根据互动通知编号回复评论，由 CLI 补全帖子、评论及回复人信息
     *
     * @param noticeRef 通知编号，如通知 #1 对应 1
     * @param text 回复正文
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     */
    public static ChannelCliResult sendReply(int noticeRef, String text) {
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", noticeRef);
        parameters.put("reply_type", 1);
        parameters.put("content", text);
        return doReply(parameters);
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
     * 查看帖子评论
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code feed.get-feed-comments}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getFeedComments(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getFeedComments(JsonNode parameters) {
        return getFeedComments(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看帖子评论，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 feed get-feed-comments 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getFeedComments(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("feed", "get-feed-comments", parameters, options);
    }

    /**
     * 查看更多回复（评论回复分页）
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code feed.get-next-page-replies}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #getNextPageReplies(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult getNextPageReplies(JsonNode parameters) {
        return getNextPageReplies(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看更多回复（评论回复分页），支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 feed get-next-page-replies 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult getNextPageReplies(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("feed", "get-next-page-replies", parameters, options);
    }

    /**
     * 发表/删除评论
     * 涉及删除等需 --yes 的操作时，使用带 options 的重载传入 CONFIRMED。
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code feed.do-comment}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #doComment(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult doComment(JsonNode parameters) {
        return doComment(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 发表/删除评论，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 feed do-comment 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult doComment(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("feed", "do-comment", parameters, options);
    }

    /**
     * 发表/删除回复
     * 涉及删除等需 --yes 的操作时，使用带 options 的重载传入 CONFIRMED。
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code feed.do-reply}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #doReply(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult doReply(JsonNode parameters) {
        return doReply(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 发表/删除回复，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 feed do-reply 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult doReply(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("feed", "do-reply", parameters, options);
    }

    /**
     * 评论/回复点赞
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code feed.do-like}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #doLike(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult doLike(JsonNode parameters) {
        return doLike(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 评论/回复点赞，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 feed do-like 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult doLike(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("feed", "do-like", parameters, options);
    }

    /**
     * 搜索帖子并评论
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code feed.search-and-comment}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #searchAndComment(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult searchAndComment(JsonNode parameters) {
        return searchAndComment(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 搜索帖子并评论，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 feed search-and-comment 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult searchAndComment(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("feed", "search-and-comment", parameters, options);
    }

    /**
     * 搜帖删帖并禁言
     * 涉及删除等需 --yes 的操作时，使用带 options 的重载传入 CONFIRMED。
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code feed.delete-and-mute}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #deleteAndMute(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult deleteAndMute(JsonNode parameters) {
        return deleteAndMute(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 搜帖删帖并禁言，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 feed delete-and-mute 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult deleteAndMute(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("feed", "delete-and-mute", parameters, options);
    }

    /**
     * 查看帖子评论
     *
     * @param guildId 腾讯频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param count 每页数量 (最大 20)，null 时沿用接口默认值
     * @param rankType 排序：0=默认，1=时间正序，2=时间倒序，null 时沿用接口默认值
     * @param replyListNum 每条评论预加载的回复数（默认1，最大10），null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getFeedComments(String guildId, String channelId, String feedId, Integer count, Integer rankType, Integer replyListNum, String attachInfo) {
        return getFeedComments(guildId, channelId, feedId, count, rankType, replyListNum, attachInfo, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看帖子评论，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return getFeedComments(parameters, options);
    }

    /**
     * 查看更多回复（评论回复分页）
     *
     * @param guildId 腾讯频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param commentId 评论 ID
     * @param count 每页数量 (最大 50)，null 时沿用接口默认值
     * @param attachInfo 翻页令牌 (首次从 get-feed-comments 评论对象的 attach_info 获取, 后续从本命令返回获取)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getNextPageReplies(String guildId, String channelId, String feedId, String commentId, Integer count, String attachInfo) {
        return getNextPageReplies(guildId, channelId, feedId, commentId, count, attachInfo, ChannelCliOptions.DEFAULT);
    }

    /**
     * 查看更多回复（评论回复分页），支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return getNextPageReplies(parameters, options);
    }

    /**
     * 按互动通知编号发表评论
     *
     * @param ref 通知编号（如 #1），按编号自动填充帖子信息
     * @param content 评论内容 (发表时与 image-path 至少填一个)
     * @param imagePath 评论图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendComment(Integer ref, String content, String imagePath) {
        return sendComment(ref, content, imagePath, ChannelCliOptions.DEFAULT);
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
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", ref);
        parameters.put("content", content);
        if (imagePath != null) parameters.put("image_path", imagePath);
        parameters.put("comment_type", 1);
        return doComment(parameters, options);
    }

    /**
     * 按互动通知编号回复评论
     *
     * @param ref 通知编号（如 #1），按编号自动填充帖子和评论信息
     * @param content 回复内容 (发表时与 image-path 至少填一个)
     * @param imagePath 回复图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendReply(Integer ref, String content, String imagePath) {
        return sendReply(ref, content, imagePath, ChannelCliOptions.DEFAULT);
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
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("ref", ref);
        parameters.put("content", content);
        if (imagePath != null) parameters.put("image_path", imagePath);
        parameters.put("reply_type", 1);
        return doReply(parameters, options);
    }

    /**
     * 直接指定帖子发表评论
     *
     * @param target 帖子的完整定位信息
     * @param content 评论内容 (发表时与 image-path 至少填一个)
     * @param imagePath 评论图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendCommentTo(FeedTarget target, String content, String imagePath) {
        return sendCommentTo(target, content, imagePath, ChannelCliOptions.DEFAULT);
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
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        java.util.Objects.requireNonNull(target, "target");
        var parameters = feedParameters(target);
        parameters.put("content", content);
        if (imagePath != null) parameters.put("image_path", imagePath);
        parameters.put("comment_type", 1);
        return doComment(parameters, options);
    }

    /**
     * 删除自己的评论
     *
     * @param target 帖子的完整定位信息
     * @param commentId 评论 ID (删除时必填)
     * @param commentAuthorId 评论作者 ID (删除时必填)
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteComment(FeedTarget target, String commentId, String commentAuthorId) {
        return deleteComment(target, commentId, commentAuthorId, ChannelCliOptions.DEFAULT);
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
        return doComment(parameters, options);
    }

    /**
     * 以帖主身份删除评论
     *
     * @param target 帖子的完整定位信息
     * @param commentId 评论 ID (删除时必填)
     * @param commentAuthorId 评论作者 ID (删除时必填)
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteCommentAsOwner(FeedTarget target, String commentId, String commentAuthorId) {
        return deleteCommentAsOwner(target, commentId, commentAuthorId, ChannelCliOptions.DEFAULT);
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
        return doComment(parameters, options);
    }

    /**
     * 搜索帖子并评论
     *
     * @param guildId 频道 ID
     * @param query 搜索关键词
     * @param content 评论内容
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchAndComment(String guildId, String query, String content) {
        return searchAndComment(guildId, query, content, ChannelCliOptions.DEFAULT);
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
        return searchAndComment(parameters, options);
    }

    /**
     * 搜索帖子并评论
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeSearchAndComment(String resumeId, String pick) {
        return resumeSearchAndComment(resumeId, pick, ChannelCliOptions.DEFAULT);
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
        return searchAndComment(parameters, options);
    }

    /**
     * 搜帖删帖并禁言
     *
     * @param guildId 频道 ID
     * @param query 搜索关键词
     * @param timeStamp 禁言时长（秒），null 时沿用接口默认值
     * @param set resume 时传入参数 (key=value)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchDeleteAndMute(String guildId, String query, String timeStamp, String set) {
        return searchDeleteAndMute(guildId, query, timeStamp, set, ChannelCliOptions.DEFAULT);
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
        return deleteAndMute(parameters, options);
    }

    /**
     * 搜帖删帖并禁言
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @param timeStamp 禁言时长（秒），null 时沿用接口默认值
     * @param set resume 时传入参数 (key=value)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeDeleteAndMute(String resumeId, String pick, String timeStamp, String set) {
        return resumeDeleteAndMute(resumeId, pick, timeStamp, set, ChannelCliOptions.DEFAULT);
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
        return deleteAndMute(parameters, options);
    }

    /**
     * 点赞评论
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult likeComment(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId) {
        return likeComment(target, feedAuthorId, commentId, commentAuthorId, ChannelCliOptions.DEFAULT);
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
        return doLike(parameters, options);
    }

    /**
     * 取消评论点赞
     *
     * @param target 帖子的完整定位信息
     * @param feedAuthorId 帖子作者 ID
     * @param commentId 评论 ID
     * @param commentAuthorId 评论作者 ID
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult unlikeComment(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId) {
        return unlikeComment(target, feedAuthorId, commentId, commentAuthorId, ChannelCliOptions.DEFAULT);
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
        return doLike(parameters, options);
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
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult likeReply(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId) {
        return likeReply(target, feedAuthorId, commentId, commentAuthorId, replyId, replyAuthorId, ChannelCliOptions.DEFAULT);
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
        return doLike(parameters, options);
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
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult unlikeReply(FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId) {
        return unlikeReply(target, feedAuthorId, commentId, commentAuthorId, replyId, replyAuthorId, ChannelCliOptions.DEFAULT);
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
        return doLike(parameters, options);
    }

    /**
     * 删除回复（本人）
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replyId 回复 ID (删除时必填)
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteReply(CommentTarget target, String replyId, String replierId) {
        return deleteReply(target, replyId, replierId, ChannelCliOptions.DEFAULT);
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
        return doReply(parameters, options);
    }

    /**
     * 删除回复（帖主）
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replyId 回复 ID (删除时必填)
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteReplyAsOwner(CommentTarget target, String replyId, String replierId) {
        return deleteReplyAsOwner(target, replyId, replierId, ChannelCliOptions.DEFAULT);
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
        return doReply(parameters, options);
    }

    /**
     * 直接指定评论回复
     *
     * @param target 评论及所属帖子的完整定位信息
     * @param replierId 回复人用户 ID（发表回复时必填，删除时可选）
     * @param content 回复内容 (发表时与 image-path 至少填一个)
     * @param imagePath 回复图片路径 (最多 1 张, 自动上传)，null 时沿用接口默认值
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sendReplyTo(CommentTarget target, String replierId, String content, String imagePath) {
        return sendReplyTo(target, replierId, content, imagePath, ChannelCliOptions.DEFAULT);
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
        if (replierId == null || replierId.isBlank()) throw new IllegalArgumentException("replier-id 不能为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
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
        parameters.put("reply_type", 1);
        return doReply(parameters, options);
    }

}
