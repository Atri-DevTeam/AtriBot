package top.yzljc.atribot.function.admin.ema;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.sakuraba_ema.ChannelSystem;
import top.yzljc.sakuraba_ema.guild.*;
import top.yzljc.sakuraba_ema.guild.management.*;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;
import top.yzljc.atribot.function.admin.ema.EmaArguments.Kind;
import top.yzljc.atribot.function.admin.ema.EmaArguments.Parameter;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * @Author YZ_Ljc_
 * @ClassName EmaRoutes
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.admin.ema
 * @Description 子指令及参数定义，直接调用有类型的业务方法
 */
final class EmaRoutes {
    static final Map<String, Route> ALL = createRoutes();

    private EmaRoutes() {
    }

    record Route(String path, String description, List<Parameter> parameters, String example,
                 boolean supportsOptions, Function<EmaArguments, ChannelCliResult> call) {
        String usage() {
            StringBuilder text = new StringBuilder("/ema ").append(path);
            if (supportsOptions) text.append(" [--yes] [--dry-run]");
            parameters.stream().filter(Parameter::optional).forEach(p -> text.append(' ').append(p.usage()));
            parameters.stream().filter(p -> !p.optional()).forEach(p -> text.append(' ').append(p.usage()));
            return text.toString();
        }
    }

    private static Map<String, Route> createRoutes() {
        Map<String, Route> routes = new LinkedHashMap<>();
        add(routes, "guild info", "查看腾讯频道资料", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of())),
                a -> ChannelInformation.getGuildInfo(a.text("guild-id"), a.options()));
        add(routes, "guild list", "查看我的腾讯频道列表", "", true,
                List.of(),
                a -> ChannelInformation.getMyJoinGuildInfo(a.options()));
        add(routes, "guild channel list", "查看版块列表", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of())),
                a -> ChannelInformation.getGuildChannelList(a.text("guild-id"), a.options()));
        add(routes, "guild search", "搜索腾讯频道/帖子/作者", "示例", true,
                List.of(p("keyword", "关键词", Kind.TEXT, false, true, "搜索关键词", List.of()),
                        p("scope", "搜索范围", Kind.TEXT, true, false, "搜索范围: channel|feed|author|all", List.of("channel", "feed", "author", "all")),
                        p("next-page-token", "next-page-token", Kind.TEXT, true, false, "翻页令牌 (从上次返回结果获取)", List.of())),
                a -> ChannelInformation.searchGuildContent(a.text("keyword"), a.text("scope"), a.text("next-page-token"), a.options()));
        add(routes, "guild admission get", "查看腾讯频道加入设置", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of())),
                a -> ChannelInformation.getJoinGuildSetting(a.text("guild-id"), a.options()));
        add(routes, "guild share url", "获取腾讯频道分享短链", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of())),
                a -> ChannelInformation.getGuildShareUrl(a.text("guild-id"), a.options()));
        add(routes, "guild share info", "查看分享链接信息", "https://example.com/a.png", true,
                List.of(p("url", "链接", Kind.TEXT, false, false, "pd.qq.com 分享链接", List.of())),
                a -> ChannelInformation.getShareInfo(a.text("url"), a.options()));
        add(routes, "guild edit", "修改腾讯频道名称/简介", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("guild-name", "频道名称", Kind.TEXT, true, false, "新腾讯频道名称", List.of()),
                        p("guild-profile", "频道简介", Kind.TEXT, true, false, "新腾讯频道简介", List.of())),
                a -> ChannelManagement.updateGuildInfo(a.text("guild-id"), a.text("guild-name"), a.text("guild-profile"), a.options()));
        add(routes, "guild number", "修改腾讯频道号", "123 示例", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("guild-number", "频道号", Kind.TEXT, false, false, "新腾讯频道号", List.of())),
                a -> ChannelManagement.modifyGuildNumber(a.text("guild-id"), a.text("guild-number"), a.options()));
        add(routes, "guild join", "加入腾讯频道", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of())),
                a -> ChannelManagement.joinGuild(a.text("guild-id"), a.options()));
        add(routes, "guild channel create", "创建子版块", "123 示例", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-name", "版块名称", Kind.TEXT, false, true, "版块名称", List.of())),
                a -> ChannelManagement.createChannel(a.text("guild-id"), a.text("channel-name"), a.options()));
        add(routes, "guild channel delete", "删除版块", "123 456,789", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-ids", "版块ID列表", Kind.LIST, false, false, "要删除的版块 ID 列表；多个值用逗号分隔", List.of())),
                a -> ChannelManagement.deleteChannel(a.text("guild-id"), a.list("channel-ids"), a.options()));
        add(routes, "guild channel edit", "修改版块名称", "123 456 示例", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("channel-name", "版块名称", Kind.TEXT, false, true, "新版块名称", List.of())),
                a -> ChannelManagement.modifyChannel(a.text("guild-id"), a.text("channel-id"), a.text("channel-name"), a.options()));
        add(routes, "guild avatar", "修改腾讯频道头像", "123 C:/images/a.png", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("image-path", "图片路径", Kind.TEXT, false, false, "头像图片路径", List.of())),
                a -> ChannelManagement.uploadGuildAvatar(a.text("guild-id"), a.text("image-path"), a.options()));
        add(routes, "guild create", "创建频道(公开/私密)", "C:/images/a.png 示例", true,
                List.of(p("image-path", "图片路径", Kind.TEXT, false, false, "头像图片路径", List.of()),
                        p("guild-name", "频道名称", Kind.TEXT, false, true, "腾讯频道名称 (≤15字)", List.of()),
                        p("theme", "theme", Kind.TEXT, true, false, "主题关键词 (用于自动生成名称和简介)", List.of()),
                        p("guild-profile", "频道简介", Kind.TEXT, true, false, "腾讯频道简介 (≤300字符)", List.of()),
                        p("community-type", "community-type", Kind.TEXT, true, false, "腾讯频道类型: public|private|公开|私密", List.of("public", "private", "公开", "私密"))),
                a -> ChannelManagement.createThemePrivateGuild(a.text("image-path"), a.text("theme"), a.text("guild-name"), a.text("guild-profile"), a.text("community-type"), a.options()));
        add(routes, "guild admission set", "修改腾讯频道加入设置", "123 JOIN_GUILD_TYPE_DIRECT", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("join-type", "入频方式", Kind.TEXT, false, false, "加入方式", List.of("JOIN_GUILD_TYPE_DIRECT", "JOIN_GUILD_TYPE_ADMIN_AUDIT", "JOIN_GUILD_TYPE_DISABLE", "JOIN_GUILD_TYPE_QUESTION_WITH_ADMIN_AUDIT", "JOIN_GUILD_TYPE_MULTI_QUESTION", "JOIN_GUILD_TYPE_QUIZ"))),
                a -> ChannelManagement.updateJoinGuildSetting(a.text("guild-id"), a.text("join-type"), a.options()));
        add(routes, "guild leave", "退出腾讯频道", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of())),
                a -> ChannelManagement.leaveGuild(a.text("guild-id"), a.options()));
        add(routes, "guild search-join", "搜索频道并加入", "示例", true,
                List.of(p("keyword", "关键词", Kind.TEXT, false, true, "搜索关键词", List.of())),
                a -> ChannelManagement.searchAndJoin(a.text("keyword"), a.options()));
        add(routes, "guild search-join resume", "搜索频道并加入", "s-xxx 0", true,
                List.of(p("resume-id", "续办ID", Kind.TEXT, false, false, "resume session ID", List.of()),
                        p("pick", "选择编号", Kind.TEXT, false, false, "resume 时的选择索引", List.of())),
                a -> ChannelManagement.resumeSearchAndJoin(a.text("resume-id"), a.text("pick"), a.options()));
        add(routes, "member get", "查看用户资料", "123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("tiny-id", "成员ID", Kind.TEXT, false, false, "成员 Tiny ID", List.of())),
                a -> ChannelMembers.getUserInfo(a.text("guild-id"), a.text("tiny-id"), a.options()));
        add(routes, "member list", "查看成员列表（分页）", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("next-page-token", "next-page-token", Kind.TEXT, true, false, "翻页令牌", List.of())),
                a -> ChannelMembers.getGuildMemberList(a.text("guild-id"), a.text("next-page-token"), a.options()));
        add(routes, "member search", "按昵称搜索成员", "123 示例", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("keyword", "关键词", Kind.TEXT, false, true, "搜索关键词", List.of()),
                        p("num", "数量", Kind.INTEGER, true, false, "每页数量", List.of()),
                        p("next-pos", "next-pos", Kind.TEXT, true, false, "翻页位置 (从上次返回结果获取)", List.of())),
                a -> ChannelMembers.guildMemberSearch(a.text("guild-id"), a.text("keyword"), a.number("num"), a.text("next-pos"), a.options()));
        add(routes, "member kick", "踢出成员", "123 456,789", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("member-tinyids", "成员ID列表", Kind.LIST, false, false, "成员 Tiny ID 列表 (批量踢人)；多个值用逗号分隔", List.of()),
                        p("blacklist", "同时拉黑", Kind.BOOLEAN, true, false, "同时拉黑该成员", List.of()),
                        p("revoke-msgs", "撤回范围", Kind.TEXT, true, false, "撤回该成员的消息: 3d|7d|15d|30d|all", List.of("3d", "7d", "15d", "30d", "all"))),
                a -> ChannelMembers.kickMembers(a.text("guild-id"), a.list("member-tinyids"), a.bool("blacklist"), a.text("revoke-msgs"), a.options()));
        add(routes, "member mute", "禁言/解禁成员", "123 456 10m", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("tiny-id", "成员ID", Kind.TEXT, false, false, "成员 Tiny ID", List.of()),
                        p("duration", "时长", Kind.DURATION, false, false, "支持 s/m/h/d，不带单位按秒", List.of())),
                a -> ChannelMembers.muteMember(a.text("guild-id"), a.text("tiny-id"), a.duration("duration"), a.options()));
        add(routes, "member unmute", "禁言/解禁成员", "123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("tiny-id", "成员ID", Kind.TEXT, false, false, "成员 Tiny ID", List.of())),
                a -> ChannelMembers.unmuteMember(a.text("guild-id"), a.text("tiny-id"), a.options()));
        add(routes, "role create", "创建身份组", "123 示例", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("name", "名称", Kind.TEXT, false, true, "身份组名称（最多 30 个字符，中文算 2 个、英文数字算 1 个）", List.of())),
                a -> ChannelRoles.createGuildRoleGroup(a.text("guild-id"), a.text("name"), a.options()));
        add(routes, "role edit", "修改身份组", "123 456 示例", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("role-id", "身份组ID", Kind.TEXT, false, false, "身份组 ID", List.of()),
                        p("name", "名称", Kind.TEXT, false, true, "新的身份组名称（最多 30 个字符，中文算 2 个、英文数字算 1 个）", List.of())),
                a -> ChannelRoles.modifyGuildRoleGroup(a.text("guild-id"), a.text("role-id"), a.text("name"), a.options()));
        add(routes, "role member add", "向身份组添加成员", "123 456 456,789", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("role-id", "身份组ID", Kind.TEXT, false, false, "身份组 ID", List.of()),
                        p("tiny-ids", "成员ID列表", Kind.LIST, false, false, "成员 Tiny ID 列表；多个值用逗号分隔", List.of())),
                a -> ChannelRoles.addRoleMembers(a.text("guild-id"), a.text("role-id"), a.list("tiny-ids"), a.options()));
        add(routes, "role member remove", "从身份组移除成员", "123 456 456,789", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("role-id", "身份组ID", Kind.TEXT, false, false, "身份组 ID", List.of()),
                        p("tiny-ids", "成员ID列表", Kind.LIST, false, false, "成员 Tiny ID 列表；多个值用逗号分隔", List.of())),
                a -> ChannelRoles.removeRoleMembers(a.text("guild-id"), a.text("role-id"), a.list("tiny-ids"), a.options()));
        add(routes, "role admin add", "设置超级管理员", "123 456,789", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("tiny-ids", "成员ID列表", Kind.LIST, false, false, "成员 Tiny ID 列表；多个值用逗号分隔", List.of())),
                a -> ChannelRoles.addAdmin(a.text("guild-id"), a.list("tiny-ids"), a.options()));
        add(routes, "role admin remove", "移除超级管理员", "123 456,789", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("tiny-ids", "成员ID列表", Kind.LIST, false, false, "成员 Tiny ID 列表；多个值用逗号分隔", List.of())),
                a -> ChannelRoles.removeAdmin(a.text("guild-id"), a.list("tiny-ids"), a.options()));
        add(routes, "dm send", "向目标用户发送频道私信信息", "123 456 你好 世界", true,
                List.of(p("source-guild-id", "频道ID", Kind.TEXT, false, false, "来源腾讯频道 ID", List.of()),
                        p("peer-tiny-id", "用户ID", Kind.TEXT, false, false, "目标用户的tinyID", List.of()),
                        p("text", "正文", Kind.TEXT, false, true, "消息文本内容", List.of())),
                a -> ChannelPrivateChat.sendMessage(a.text("source-guild-id"), a.text("peer-tiny-id"), a.text("text"), a.options()));
        add(routes, "dm reply", "向目标用户发送频道私信信息", "1 你好 世界", true,
                List.of(p("ref", "通知编号", Kind.INTEGER, false, false, "通知编号（如 #1），按编号查找本地私信通知自动填充 peer-tiny-id 和 source-guild-id", List.of()),
                        p("text", "正文", Kind.TEXT, false, true, "消息文本内容", List.of())),
                a -> ChannelPrivateChat.replyMessage(a.number("ref"), a.text("text"), a.options()));
        add(routes, "notice interactions", "查看互动消息", "", true,
                List.of(p("page-num", "数量", Kind.INTEGER, true, false, "每页数量", List.of()),
                        p("guild-id", "频道ID", Kind.TEXT, true, false, "腾讯频道 ID (可选, 筛选特定腾讯频道)", List.of()),
                        p("attach-info", "attach-info", Kind.TEXT, true, false, "翻页令牌 (从上次返回结果获取)", List.of())),
                a -> ChannelNotices.getNotices(a.number("page-num"), a.text("guild-id"), a.text("attach-info"), a.options()));
        add(routes, "notice push on", "开启频道消息通知（有新互动时自动推送）", "", true,
                List.of(p("session-key", "session-key", Kind.TEXT, true, false, "当前会话的 sessionKey (如 agent:main:)，CLI 自动解析路由信息", List.of()),
                        p("confirm", "confirm", Kind.BOOLEAN, true, false, "确认测试推送成功，正式开启订阅", List.of())),
                a -> ChannelNotices.noticesOn(a.text("session-key"), a.bool("confirm"), a.options()));
        add(routes, "notice push off", "关闭频道消息通知", "", true,
                List.of(p("session-key", "session-key", Kind.TEXT, true, false, "要移除的 sessionKey（仅移除该通道的推送路由，其他通道不受影响）", List.of())),
                a -> ChannelNotices.noticesOff(a.text("session-key"), a.options()));
        add(routes, "notice push status", "查看频道消息通知状态", "", true,
                List.of(),
                a -> ChannelNotices.noticesStatus(a.options()));
        add(routes, "notice check", "检查新的频道通知（增量）", "", true,
                List.of(),
                a -> ChannelNotices.checkNotices(a.options()));
        add(routes, "notice subscribe", "开启频道消息通知", "", true,
                List.of(),
                a -> ChannelNotices.subscribeNotices(a.options()));
        add(routes, "notice unsubscribe", "关闭频道消息通知", "", true,
                List.of(),
                a -> ChannelNotices.unsubscribeNotices(a.options()));
        add(routes, "notice check-new", "检查新的频道通知", "", true,
                List.of(),
                a -> ChannelNotices.checkNewNotices(a.options()));
        add(routes, "notice recent", "获取最近的通知记录（本地）", "", true,
                List.of(),
                a -> ChannelNotices.getRecentNotices(a.options()));
        add(routes, "notice agree", "同意通知中的申请", "1", true,
                List.of(p("ref", "通知编号", Kind.INTEGER, false, false, "通知编号（如 #1 中的 1），按编号查找本地通知自动填充 notice-id", List.of())),
                a -> ChannelNotices.agreeNotice(a.number("ref"), a.options()));
        add(routes, "notice refuse", "拒绝通知中的申请", "1", true,
                List.of(p("ref", "通知编号", Kind.INTEGER, false, false, "通知编号（如 #1 中的 1），按编号查找本地通知自动填充 notice-id", List.of())),
                a -> ChannelNotices.refuseNotice(a.number("ref"), a.options()));
        add(routes, "notice agree-id", "按通知 ID 同意申请", "456", true,
                List.of(p("notice-id", "通知ID", Kind.TEXT, false, false, "通知 ID", List.of())),
                a -> ChannelNotices.agreeNotice(a.text("notice-id"), a.options()));
        add(routes, "notice refuse-id", "按通知 ID 拒绝申请", "456", true,
                List.of(p("notice-id", "通知ID", Kind.TEXT, false, false, "通知 ID", List.of())),
                a -> ChannelNotices.refuseNotice(a.text("notice-id"), a.options()));
        add(routes, "post list", "获取腾讯频道主页帖子", "123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("get-type", "get-type", Kind.INTEGER, true, false, "获取类型: 1=热门 2=最新", List.of("1", "2")),
                        p("count", "数量", Kind.INTEGER, true, false, "每页数量", List.of()),
                        p("feed-attach-info", "feed-attach-info", Kind.TEXT, true, false, "翻页令牌 (从上次返回结果获取)", List.of())),
                a -> ChannelPosts.getGuildFeeds(a.text("guild-id"), a.number("get-type"), a.number("count"), a.text("feed-attach-info"), a.options()));
        add(routes, "post channel list", "获取版块帖子列表", "123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("count", "数量", Kind.INTEGER, true, false, "每页数量", List.of()),
                        p("feed-attach-info", "feed-attach-info", Kind.TEXT, true, false, "翻页令牌 (从上次返回结果获取)", List.of())),
                a -> ChannelPosts.getChannelTimelineFeeds(a.text("guild-id"), a.text("channel-id"), a.number("count"), a.text("feed-attach-info"), a.options()));
        add(routes, "post get", "查看帖子详情", "123 456 B_123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID (推荐传入)", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of())),
                a -> ChannelPosts.getPost(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.options()));
        add(routes, "post search", "搜索帖子", "123 示例", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("query", "关键词", Kind.TEXT, false, true, "搜索关键词", List.of()),
                        p("next-page-cookie", "next-page-cookie", Kind.TEXT, true, false, "翻页令牌 (从上次返回结果获取)", List.of())),
                a -> ChannelPosts.searchGuildFeeds(a.text("guild-id"), a.text("query"), a.text("next-page-cookie"), a.options()));
        add(routes, "post share", "获取帖子分享短链", "123 456 B_123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of())),
                a -> ChannelPosts.sharePost(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.options()));
        add(routes, "post publish", "发表帖子", "123 456 你好 世界", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)", List.of()),
                        p("title", "标题", Kind.TEXT, true, false, "帖子标题 (长贴必填, 有标题自动升级为长贴)", List.of())),
                a -> ChannelPosts.publishText(a.text("guild-id"), a.text("channel-id"), a.text("content"), a.text("title"), a.options()));
        add(routes, "post publish images", "发表帖子", "123 456 C:/images/a.png,C:/images/b.png 你好 世界", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）", List.of()),
                        p("file-paths", "图片路径列表", Kind.LIST, false, false, "file-paths；多个值用逗号分隔", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)", List.of()),
                        p("title", "标题", Kind.TEXT, true, false, "帖子标题 (长贴必填, 有标题自动升级为长贴)", List.of())),
                a -> ChannelPosts.publishImages(a.text("guild-id"), a.text("channel-id"), a.list("file-paths"), a.text("content"), a.text("title"), a.options()));
        add(routes, "post delete", "删除帖子", "123 456 B_123 1700000000", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("create-time", "创建时间戳", Kind.TEXT, false, false, "创建时间戳", List.of())),
                a -> ChannelPosts.deleteFeed(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("create-time"), a.options()));
        add(routes, "post like", "点赞帖子", "123 456 B_123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of())),
                a -> ChannelPosts.likeFeed(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.options()));
        add(routes, "post unlike", "取消帖子点赞", "123 456 B_123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of())),
                a -> ChannelPosts.unlikeFeed(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.options()));
        add(routes, "post edit", "编辑帖子", "123 456 B_123 1700000000 你好 世界", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("create-time", "创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "新内容（普通文本模式；与 --markdown-content 互斥）", List.of()),
                        p("title", "标题", Kind.TEXT, true, false, "新标题", List.of())),
                a -> ChannelPosts.editText(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("create-time"), a.text("content"), a.text("title"), a.options()));
        add(routes, "post edit markdown", "编辑帖子", "123 456 B_123 1700000000 你好 世界", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("create-time", "创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳", List.of()),
                        p("markdown-content", "Markdown正文", Kind.TEXT, false, true, "Markdown 正文（仅长贴; 短贴自动降级; 与 --content 互斥）", List.of()),
                        p("title", "标题", Kind.TEXT, true, false, "新标题", List.of())),
                a -> ChannelPosts.editMarkdown(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("create-time"), a.text("markdown-content"), a.text("title"), a.options()));
        add(routes, "post pin", "置顶帖子", "123 B_123 456 1700000000", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("user-id", "作者ID", Kind.TEXT, false, false, "帖子发表者用户 ID", List.of()),
                        p("create-time", "创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳", List.of())),
                a -> ChannelPosts.pinFeed(a.text("guild-id"), a.text("feed-id"), a.text("user-id"), a.text("create-time"), a.options()));
        add(routes, "post unpin", "取消置顶帖子", "123 B_123 456 1700000000", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("user-id", "作者ID", Kind.TEXT, false, false, "帖子发表者用户 ID", List.of()),
                        p("create-time", "创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳", List.of())),
                a -> ChannelPosts.unpinFeed(a.text("guild-id"), a.text("feed-id"), a.text("user-id"), a.text("create-time"), a.options()));
        add(routes, "post essence set", "设置精华帖子", "B_123", true,
                List.of(p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of())),
                a -> ChannelPosts.markEssence(a.text("feed-id"), a.options()));
        add(routes, "post essence remove", "取消精华帖子", "B_123", true,
                List.of(p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of())),
                a -> ChannelPosts.removeEssence(a.text("feed-id"), a.options()));
        add(routes, "post essence push", "推送精华帖通知", "B_123", true,
                List.of(p("feed-id", "帖子ID", Kind.TEXT, false, false, "精华帖 ID (需先设置为精华)", List.of())),
                a -> ChannelPosts.pushEssenceFeed(a.text("feed-id"), a.options()));
        add(routes, "post move", "移动帖子到其他版块", "123 456 456 B_123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "频道 ID", List.of()),
                        p("original-channel-id", "原版块ID", Kind.TEXT, false, false, "帖子当前所在版块 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "目标版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of())),
                a -> ChannelPosts.movePost(a.text("guild-id"), a.text("original-channel-id"), a.text("channel-id"), a.text("feed-id"), a.options()));
        add(routes, "post quick publish", "选择频道和版块，一键发帖", "你好 世界", true,
                List.of(p("content", "正文", Kind.TEXT, false, true, "帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)", List.of()),
                        p("title", "标题", Kind.TEXT, true, false, "帖子标题 (有标题自动升级为长贴)", List.of())),
                a -> ChannelPosts.quickPublishText(a.text("content"), a.text("title"), a.options()));
        add(routes, "post quick resume", "选择频道和版块，一键发帖", "s-xxx 0", true,
                List.of(p("resume-id", "续办ID", Kind.TEXT, false, false, "resume session ID", List.of()),
                        p("pick", "选择编号", Kind.TEXT, false, false, "resume 时的选择索引", List.of())),
                a -> ChannelPosts.resumeQuickPublish(a.text("resume-id"), a.text("pick"), a.options()));
        add(routes, "post latest", "获取频道最新帖子详情", "", true,
                List.of(p("count", "数量", Kind.INTEGER, true, false, "帖子数量", List.of())),
                a -> ChannelPosts.latestFeedsDetail(a.number("count"), a.options()));
        add(routes, "post latest resume", "获取频道最新帖子详情", "s-xxx 0", true,
                List.of(p("resume-id", "续办ID", Kind.TEXT, false, false, "resume session ID", List.of()),
                        p("pick", "选择编号", Kind.TEXT, false, false, "resume 时的选择索引", List.of())),
                a -> ChannelPosts.resumeLatestFeedsDetail(a.text("resume-id"), a.text("pick"), a.options()));
        add(routes, "post hot", "获取频道热门帖子详情", "", true,
                List.of(p("count", "数量", Kind.INTEGER, true, false, "帖子数量", List.of())),
                a -> ChannelPosts.hotFeedsDetail(a.number("count"), a.options()));
        add(routes, "post hot resume", "获取频道热门帖子详情", "s-xxx 0", true,
                List.of(p("resume-id", "续办ID", Kind.TEXT, false, false, "resume session ID", List.of()),
                        p("pick", "选择编号", Kind.TEXT, false, false, "resume 时的选择索引", List.of())),
                a -> ChannelPosts.resumeHotFeedsDetail(a.text("resume-id"), a.text("pick"), a.options()));
        add(routes, "comment list", "查看帖子评论", "123 456 B_123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("count", "数量", Kind.INTEGER, true, false, "每页数量 (最大 20)", List.of()),
                        p("rank-type", "rank-type", Kind.INTEGER, true, false, "排序：0=默认，1=时间正序，2=时间倒序", List.of()),
                        p("reply-list-num", "reply-list-num", Kind.INTEGER, true, false, "每条评论预加载的回复数（默认1，最大10）", List.of()),
                        p("attach-info", "attach-info", Kind.TEXT, true, false, "翻页令牌 (从上次返回结果获取)", List.of())),
                a -> ChannelComments.getFeedComments(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.number("count"), a.number("rank-type"), a.number("reply-list-num"), a.text("attach-info"), a.options()));
        add(routes, "comment replies", "查看更多回复（评论回复分页）", "123 456 B_123 C_123", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID", List.of()),
                        p("count", "数量", Kind.INTEGER, true, false, "每页数量 (最大 50)", List.of()),
                        p("attach-info", "attach-info", Kind.TEXT, true, false, "翻页令牌 (首次从 get-feed-comments 评论对象的 attach_info 获取, 后续从本命令返回获取)", List.of())),
                a -> ChannelComments.getNextPageReplies(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("comment-id"), a.number("count"), a.text("attach-info"), a.options()));
        add(routes, "comment send", "按互动通知编号发表评论", "1 你好 世界", true,
                List.of(p("ref", "通知编号", Kind.INTEGER, false, false, "通知编号（如 #1），按编号自动填充帖子信息", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "评论内容 (发表时与 image-path 至少填一个)", List.of()),
                        p("image-path", "图片路径", Kind.TEXT, true, false, "评论图片路径 (最多 1 张, 自动上传)", List.of())),
                a -> ChannelComments.sendComment(a.number("ref"), a.text("content"), a.text("image-path"), a.options()));
        add(routes, "comment reply", "按互动通知编号回复评论", "1 你好 世界", true,
                List.of(p("ref", "通知编号", Kind.INTEGER, false, false, "通知编号（如 #1），按编号自动填充帖子和评论信息", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "回复内容 (发表时与 image-path 至少填一个)", List.of()),
                        p("image-path", "图片路径", Kind.TEXT, true, false, "回复图片路径 (最多 1 张, 自动上传)", List.of())),
                a -> ChannelComments.sendReply(a.number("ref"), a.text("content"), a.text("image-path"), a.options()));
        add(routes, "comment send-to", "直接指定帖子发表评论", "123 456 B_123 1700000000 你好 世界", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID (建议填写)", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID (建议填写)", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "评论内容 (发表时与 image-path 至少填一个)", List.of()),
                        p("image-path", "图片路径", Kind.TEXT, true, false, "评论图片路径 (最多 1 张, 自动上传)", List.of())),
                a -> ChannelComments.sendCommentTo(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("content"), a.text("image-path"), a.options()));
        add(routes, "comment delete", "删除自己的评论", "123 456 B_123 1700000000 C_123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID (建议填写)", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID (建议填写)", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID (删除时必填)", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID (删除时必填)", List.of())),
                a -> ChannelComments.deleteComment(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("comment-id"), a.text("comment-author-id"), a.options()));
        add(routes, "comment delete-by-owner", "以帖主身份删除评论", "123 456 B_123 1700000000 C_123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID (建议填写)", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID (建议填写)", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID (删除时必填)", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID (删除时必填)", List.of())),
                a -> ChannelComments.deleteCommentAsOwner(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("comment-id"), a.text("comment-author-id"), a.options()));
        add(routes, "comment search send", "搜索帖子并评论", "123 示例 你好 世界", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "频道 ID", List.of()),
                        p("query", "关键词", Kind.TEXT, false, false, "搜索关键词", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "评论内容", List.of())),
                a -> ChannelComments.searchAndComment(a.text("guild-id"), a.text("query"), a.text("content"), a.options()));
        add(routes, "comment search resume", "搜索帖子并评论", "s-xxx 0", true,
                List.of(p("resume-id", "续办ID", Kind.TEXT, false, false, "resume session ID", List.of()),
                        p("pick", "选择编号", Kind.TEXT, false, false, "resume 时的选择索引", List.of())),
                a -> ChannelComments.resumeSearchAndComment(a.text("resume-id"), a.text("pick"), a.options()));
        add(routes, "comment delete mute", "搜帖删帖并禁言", "123 示例", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "频道 ID", List.of()),
                        p("query", "关键词", Kind.TEXT, false, true, "搜索关键词", List.of()),
                        p("time-stamp", "到期时间戳", Kind.TEXT, true, false, "禁言时长（秒）", List.of()),
                        p("set", "设置值", Kind.TEXT, true, false, "resume 时传入参数 (key=value)", List.of())),
                a -> ChannelComments.searchDeleteAndMute(a.text("guild-id"), a.text("query"), a.text("time-stamp"), a.text("set"), a.options()));
        add(routes, "comment delete mute resume", "搜帖删帖并禁言", "s-xxx 0", true,
                List.of(p("resume-id", "续办ID", Kind.TEXT, false, false, "resume session ID", List.of()),
                        p("pick", "选择编号", Kind.TEXT, false, false, "resume 时的选择索引", List.of()),
                        p("time-stamp", "到期时间戳", Kind.TEXT, true, false, "禁言时长（秒）", List.of()),
                        p("set", "设置值", Kind.TEXT, true, false, "resume 时传入参数 (key=value)", List.of())),
                a -> ChannelComments.resumeDeleteAndMute(a.text("resume-id"), a.text("pick"), a.text("time-stamp"), a.text("set"), a.options()));
        add(routes, "comment like", "点赞评论", "123 456 B_123 1700000000 456 C_123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳", List.of()),
                        p("feed-author-id", "帖子作者ID", Kind.TEXT, false, false, "帖子作者 ID", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID", List.of())),
                a -> ChannelComments.likeComment(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("feed-author-id"), a.text("comment-id"), a.text("comment-author-id"), a.options()));
        add(routes, "comment unlike", "取消评论点赞", "123 456 B_123 1700000000 456 C_123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳", List.of()),
                        p("feed-author-id", "帖子作者ID", Kind.TEXT, false, false, "帖子作者 ID", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID", List.of())),
                a -> ChannelComments.unlikeComment(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("feed-author-id"), a.text("comment-id"), a.text("comment-author-id"), a.options()));
        add(routes, "comment reply-like", "点赞回复", "123 456 B_123 1700000000 456 C_123 456 R_123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳", List.of()),
                        p("feed-author-id", "帖子作者ID", Kind.TEXT, false, false, "帖子作者 ID", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID", List.of()),
                        p("reply-id", "回复ID", Kind.TEXT, false, false, "回复 ID (点赞回复时必填)", List.of()),
                        p("reply-author-id", "回复作者ID", Kind.TEXT, false, false, "回复作者 ID (点赞回复时必填)", List.of())),
                a -> ChannelComments.likeReply(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("feed-author-id"), a.text("comment-id"), a.text("comment-author-id"), a.text("reply-id"), a.text("reply-author-id"), a.options()));
        add(routes, "comment reply-unlike", "取消回复点赞", "123 456 B_123 1700000000 456 C_123 456 R_123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳", List.of()),
                        p("feed-author-id", "帖子作者ID", Kind.TEXT, false, false, "帖子作者 ID", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID", List.of()),
                        p("reply-id", "回复ID", Kind.TEXT, false, false, "回复 ID (点赞回复时必填)", List.of()),
                        p("reply-author-id", "回复作者ID", Kind.TEXT, false, false, "回复作者 ID (点赞回复时必填)", List.of())),
                a -> ChannelComments.unlikeReply(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("feed-author-id"), a.text("comment-id"), a.text("comment-author-id"), a.text("reply-id"), a.text("reply-author-id"), a.options()));
        add(routes, "comment reply-delete", "删除回复（本人）", "123 456 B_123 1700000000 456 C_123 456 1700000000 R_123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID (建议填写, 写操作不传可能报错)", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID (建议填写, 写操作不传可能报错)", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-author-id", "帖子作者ID", Kind.TEXT, false, false, "帖子作者 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-create-time", "评论创建时间戳", Kind.TEXT, false, false, "评论创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("reply-id", "回复ID", Kind.TEXT, false, false, "回复 ID (删除时必填)", List.of()),
                        p("replier-id", "回复人ID", Kind.TEXT, false, false, "回复人用户 ID（发表回复时必填，删除时可选）", List.of())),
                a -> ChannelComments.deleteReply(new ChannelComments.CommentTarget(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("feed-author-id"), a.text("comment-id"), a.text("comment-author-id"), a.text("comment-create-time")), a.text("reply-id"), a.text("replier-id"), a.options()));
        add(routes, "comment reply-delete-by-owner", "删除回复（帖主）", "123 456 B_123 1700000000 456 C_123 456 1700000000 R_123 456", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID (建议填写, 写操作不传可能报错)", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID (建议填写, 写操作不传可能报错)", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-author-id", "帖子作者ID", Kind.TEXT, false, false, "帖子作者 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-create-time", "评论创建时间戳", Kind.TEXT, false, false, "评论创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("reply-id", "回复ID", Kind.TEXT, false, false, "回复 ID (删除时必填)", List.of()),
                        p("replier-id", "回复人ID", Kind.TEXT, false, false, "回复人用户 ID（发表回复时必填，删除时可选）", List.of())),
                a -> ChannelComments.deleteReplyAsOwner(new ChannelComments.CommentTarget(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("feed-author-id"), a.text("comment-id"), a.text("comment-author-id"), a.text("comment-create-time")), a.text("reply-id"), a.text("replier-id"), a.options()));
        add(routes, "comment reply-to", "直接指定评论回复", "123 456 B_123 1700000000 456 C_123 456 1700000000 456 你好 世界", true,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "腾讯频道 ID (建议填写, 写操作不传可能报错)", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块 ID (建议填写, 写操作不传可能报错)", List.of()),
                        p("feed-id", "帖子ID", Kind.TEXT, false, false, "帖子 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-create-time", "帖子创建时间戳", Kind.TEXT, false, false, "帖子创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("feed-author-id", "帖子作者ID", Kind.TEXT, false, false, "帖子作者 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-id", "评论ID", Kind.TEXT, false, false, "评论 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-author-id", "评论作者ID", Kind.TEXT, false, false, "评论作者 ID（直传或通过 --ref 自动填充）", List.of()),
                        p("comment-create-time", "评论创建时间戳", Kind.TEXT, false, false, "评论创建时间戳（直传或通过 --ref 自动填充）", List.of()),
                        p("replier-id", "回复人ID", Kind.TEXT, false, false, "回复人用户 ID（发表回复时必填，删除时可选）", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "回复内容 (发表时与 image-path 至少填一个)", List.of()),
                        p("image-path", "图片路径", Kind.TEXT, true, false, "回复图片路径 (最多 1 张, 自动上传)", List.of())),
                a -> ChannelComments.sendReplyTo(new ChannelComments.CommentTarget(new ChannelComments.FeedTarget(a.text("guild-id"), a.text("channel-id"), a.text("feed-id"), a.text("feed-create-time")), a.text("feed-author-id"), a.text("comment-id"), a.text("comment-author-id"), a.text("comment-create-time")), a.text("replier-id"), a.text("content"), a.text("image-path"), a.options()));
        add(routes, "system version", "查询 CLI 版本", "", false,
                List.of(),
                a -> ChannelSystem.version());
        add(routes, "system doctor", "检查 CLI 运行环境", "", false,
                List.of(),
                a -> ChannelSystem.doctor());
        add(routes, "system login start", "获取登录二维码", "", false,
                List.of(p("qrcode-path", "二维码保存路径", Kind.TEXT, true, false, "二维码保存路径", List.of()),
                        p("force", "强制重新登录", Kind.BOOLEAN, true, false, "强制重新登录", List.of())),
                a -> ChannelSystem.login(a.text("qrcode-path") == null ? null : Path.of(a.text("qrcode-path")), Boolean.TRUE.equals(a.bool("force"))));
        add(routes, "system login poll", "轮询扫码结果", "", false,
                List.of(),
                a -> ChannelSystem.pollToken());
        add(routes, "system login status", "查询登录状态", "", false,
                List.of(),
                a -> ChannelSystem.loginStatus());
        add(routes, "system schema list", "查询 CLI 命令目录", "", false,
                List.of(),
                a -> ChannelSystem.schema());
        add(routes, "system schema get", "查询指定命令资料", "feed.publish-feed", false,
                List.of(p("command", "命令名", Kind.TEXT, false, false, "命令名", List.of())),
                a -> ChannelSystem.schema(a.text("command")));
        add(routes, "system schema search", "按关键词搜索命令资料", "示例", false,
                List.of(p("keyword", "关键词", Kind.TEXT, false, true, "关键词", List.of())),
                a -> ChannelSystem.searchSchema(a.text("keyword")));
        add(routes, "post send text", "发布文本帖子", "123 456 你好 世界", false,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "频道ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块ID", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "正文", List.of())),
                a -> ChannelPosts.sendMessage(a.text("guild-id"), a.text("channel-id"), a.text("content")));
        add(routes, "post send image", "下载 URL 图片并发布帖子", "123 456 https://example.com/a.png", false,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "频道ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块ID", List.of()),
                        p("url", "链接", Kind.TEXT, false, false, "链接", List.of()),
                        p("content", "正文", Kind.TEXT, true, false, "正文", List.of())),
                a -> ChannelPosts.sendMessage(a.text("guild-id"), a.text("channel-id"), new ImageComponent(a.text("url")).setText(a.text("content"))));
        add(routes, "post send markdown", "发布 Markdown 帖子并转换 URL 配图", "123 456 示例 你好 世界", false,
                List.of(p("guild-id", "频道ID", Kind.TEXT, false, false, "频道ID", List.of()),
                        p("channel-id", "版块ID", Kind.TEXT, false, false, "版块ID", List.of()),
                        p("title", "标题", Kind.TEXT, false, false, "标题", List.of()),
                        p("content", "正文", Kind.TEXT, false, true, "正文", List.of())),
                a -> ChannelPosts.sendMessage(a.text("guild-id"), a.text("channel-id"), a.text("title"), new Markdown(a.text("content"))));

        return Collections.unmodifiableMap(routes);
    }

    private static Parameter p(String name, String label, Kind kind, boolean optional, boolean remainder,
                               String description, List<String> choices) {
        return new Parameter(name, label, kind, optional, remainder, description, choices);
    }

    private static void add(Map<String, Route> routes, String path, String description, String example,
                            boolean supportsOptions, List<Parameter> parameters,
                            Function<EmaArguments, ChannelCliResult> call) {
        routes.put(path, new Route(path, description, parameters, example, supportsOptions, call));
    }
}
