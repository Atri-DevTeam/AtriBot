# QQ 频道第二账号

[文档目录](../README.md) · [WebUI 与 Miniapp](../web.md)

该模块通过 `tencent-channel-cli` 操作第二账号，封装位于 [sakuraba_ema](../../src/main/java/top/yzljc/sakuraba_ema)。它与 QQ 官方机器人频道适配器使用不同连接和账号

## 配置

```yaml
tencent-channel:
  enabled: true
  cli-path: "tencent-channel-cli"
  login-token: ""
  timeout-seconds: 90
```

在宿主运行环境安装并登录 CLI，`cli-path` 填可执行文件或可从 PATH 查找的命令。显式配置的登录 Token 可由环境变量 `TENCENT_CHANNEL_LOGIN_TOKEN` 覆盖

未配置显式 Token 时使用 CLI 自身的登录状态。已配置显式 Token 时，本机重新扫码不会替换该配置，应同步更新对应 Token

## Java 接口

业务调用使用静态方法，参数为频道 ID、成员 ID、正文等业务字段，不需要构造 JSON 或处理 CLI 执行选项

- 查询返回 `JsonNode` 业务数据，失败返回 `null`，分页游标随数据保留
- 发送、修改、删除等操作返回 `boolean`，失败返回 `false`
- 搜索后选择、快捷发布等多步流程返回 `JsonNode`，保留候选项与 `resume_id`，由对应的 `resume...` 方法继续处理
- 方法同步等待 CLI 完成；后台任务可在项目现有异步任务中调用

```java
JsonNode guilds = ChannelInformation.getJoinedGuilds();
JsonNode members = ChannelMembers.getMemberList(guildId);
JsonNode currentUser = ChannelMembers.getCurrentUser(guildId);

boolean sent = ChannelPosts.sendMessage(guildId, channelId, "公告内容");
boolean muted = ChannelMembers.muteMember(guildId, tinyId, Duration.ofMinutes(10));
boolean removed = ChannelPosts.deleteFeed(guildId, channelId, feedId, createTime);
```

封装覆盖本地 `tencent-channel-cli 1.0.10` 命令目录中的 64 个业务命令（`feed` 24 个、`manage` 40 个），另提供登录、诊断和命令结构查询。CLI 后续新增的命令和参数需要单独适配

| 封装 | 方法与能力 |
| --- | --- |
| `ChannelInformation` | `getJoinedGuilds`、`getGuildInfo`、`getChannelList`、`searchGuildContent`、`getJoinGuildSetting`、`getGuildShareUrl`、`getShareInfo` |
| `ChannelPosts` | `getGuildFeeds`、`getChannelTimelineFeeds`、`getPost`、`searchGuildFeeds`、`sharePost`；文本、URL 图片、Markdown、本地图片发布；编辑、删除、点赞、置顶、精华、移动；快捷发布、最新及热门帖子查询和流程续办 |
| `ChannelComments` | `getFeedComments`、`getNextPageReplies`；评论与回复发送、删除、点赞及取消；`replyToReply` 回复指定回复；搜索评论、搜索删帖禁言及流程续办 |
| `ChannelPrivateChat` | `sendMessage` 发送频道私信、`replyMessage` 按通知编号回复 |
| `ChannelNotices` | 互动通知、增量通知、最近记录；开启、关闭、订阅、取消订阅；同意或拒绝申请；`notifyDaemon` 通知检查 |
| `ChannelMembers` | `getCurrentUser`、`getMemberInfo`、`getMemberList`、`searchMembers`；单人或批量移除；按时长禁言、`setMuteUntil` 设置截止时间、解除禁言 |
| `ChannelRoles` | 创建及修改身份组、增减身份组成员、增减管理员 |
| `ChannelManagement` | 修改频道资料及频道号、加入及退出、板块创建修改删除、头像、频道创建、加入设置、搜索加入及流程续办 |
| `ChannelSystem` | `version`、`doctor`、`login`、`pollToken`、`loginStatus`、`schema`、`searchSchema` |

除 `version()` 返回字符串外，系统查询返回 `JsonNode`，失败返回 `null`

### 图片、Markdown 与评论

```java
ChannelPosts.sendMessage(guildId, channelId,
        new ImageComponent(imageUrl).setText("图片说明"));

ChannelPosts.sendMessage(guildId, channelId, "公告标题",
        new Markdown("## 公告\n\n正文内容"));

ChannelPosts.publishImages(guildId, channelId,
        List.of("/data/images/example.png"), "图片说明", null);

var target = new ChannelComments.FeedTarget(guildId, channelId, feedId, createTime);
ChannelComments.sendComment(target, "评论内容");
ChannelComments.sendComment(target, null, Path.of("/data/images/example.png"));
```

URL 图片及 Markdown 中的远程配图先下载至临时文件，CLI 调用完成后清理。评论和回复允许仅发送本地图片，正文与图片至少提供一项

评论、回复操作所需的创建时间、作者 ID 等字段使用查询结果中的原值。`FeedTarget` 和 `CommentTarget` 集中保存这些定位字段

### 执行层

业务方法内部确认写入操作，不要求调用者再传 `confirmed` 或 `ChannelCliOptions`。`/ema` 管理指令继续保留 `--yes`、`--dry-run` 和详细执行结果

`internal` 中的 `PostTools`、`CommentTools`、`MemberTools` 等负责参数构造，返回 `ChannelCliResult`。该结果保留退出码、响应、标准输出、标准错误、超时和重试信息。确有预演或详细错误处理需求时使用这一层

[ChannelCliClient](../../src/main/java/top/yzljc/sakuraba_ema/ChannelCliClient.java) 统一执行进程。普通调用串行执行，限流后等待 70 秒重试一次；WebUI 使用独立的交互入口，不自动重试。`notifyDaemon` 仍受客户端进程超时限制，单次检查使用 `once=true`

原来需要读取 `ChannelCliResult` 的业务调用，应改为检查 `null` 或 `boolean`。原来的带执行选项重载迁入 `internal`，重复的 `ChannelFeedClient`、`ChannelManageClient` 原始 JSON 转发层已移除

## 管理指令

`/ema` 仅向机器人管理员开放，具体参数通过内置帮助查询：

```text
/ema help
/ema help member mute
/ema guild list
/ema guild info <频道ID>
/ema result 2
```

指令覆盖频道、成员、身份组、帖子、评论、通知和私信。操作参数与确认规则以内置帮助和执行器为准

## WebUI 频道页

入口为 `/webui/channels`。页面使用同一 CLI 账号，查询频道、板块、帖子、评论和成员，并提供内容发布及管理操作

| 路由 | 用途 |
| --- | --- |
| `GET /webui/api/channels/query/{operation}` | 查询 |
| `POST /webui/api/channels/action/{operation}` | 写入操作 |

接口沿用 WebUI 会话，写入要求 JSON、`X-Requested-With: XMLHttpRequest` 和 `confirmed: true`。后端按操作重建参数白名单，不接收任意 CLI 命令或本地文件路径

页面实时查询 CLI，不另建频道内容数据库。媒体使用返回的远程 URL。交互调用不自动重试，写入失败后需先核对实际结果再提交

未启用或未配置返回 `503`，限流返回 `429`，超时返回 `504`，CLI 业务失败返回 `502`。CLI 登录过期与 WebUI 登录会话分别处理

接口实现见 [ChannelController](../../src/main/java/top/yzljc/atribot/webui/controller/ChannelController.java)
