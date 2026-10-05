# 平台与消息

[文档目录](../README.md) · [事件](events.md) · [账号与权限](accounts.md)

## 平台划分

[Platform](../../src/main/java/top/yzljc/atribot/platform/Platform.java) 按消息场景枚举：

| 平台 | 场景 | 用户 | 消息 |
| --- | --- | --- | --- |
| QQ 官方群聊 / C2C | `OFFICIAL_GROUP`、`OFFICIAL_C2C` | `QQUser` | `QQMessage` |
| QQ 官方频道 / 频道私信 | `OFFICIAL_GUILD_CHANNEL`、`OFFICIAL_GUILD_DM` | `QQGuildUser` | `QQGuildMessage` |
| Napcat | `NAPCAT_GROUP`、`NAPCAT_PRIVATE` | `NapcatUser` | `NapcatMessage` |
| Discord | `DISCORD_GUILD`、`DISCORD_DM` | `DiscordUser` | `DiscordMessage` |
| KOOK | `KOOK_CHANNEL`、`KOOK_DM` | `KookUser` | `KookMessage` |

QQ 群聊 / C2C 与频道 / 频道私信使用不同类型，事件也分别定义。频道 ID、群 OpenID、用户 ID 和会话 ID 保留各自语义，不互相替代

## User

[User](../../src/main/java/top/yzljc/atribot/platform/User.java) 保存共同身份字段：

```java
Platform getPlatform();
boolean isBot();
String getUserId();
String getUsername();

Optional<AtriAccount> getAccount();
boolean hasPermission();
boolean hasPermission(String permission);
boolean isBlocked();
```

平台角色、原始 JSON、平台专有 ID 和发送辅助方法位于对应子类。ID 只在相应平台和字段语义内有效

`getAccount()` 查询现有绑定，可能返回空值，不负责注册账号。权限行为见 [账号与权限](accounts.md)

## Message

[Message](../../src/main/java/top/yzljc/atribot/platform/Message.java) 保存平台、消息 ID、文本、时间戳字符串和提及用户列表。附件、引用、消息类型、原始事件等字段由平台子类提供

例如 `QQMessage` 包含：

| 字段 | 用途 |
| --- | --- |
| `conversationId` | 群聊为群 OpenID，C2C 为会话用户 OpenID |
| `type`、`messageEventType` | 消息类型与来源事件类型 |
| `attachments`、`ark` | 附件与 Ark 原始数据 |
| `refIdx`、`reference` | 引用相关信息 |
| `raw` | 平台原始消息数据 |

业务需要平台能力时使用具体类型，公共逻辑只依赖所需的共同字段。不要将所有平台原始字段集中到基类

## 发送与撤回

命令回复使用 [CommandSender](../../src/main/java/top/yzljc/atribot/command/CommandSender.java)。`sendMessage(String)` 是公共文本入口；Markdown、键盘、文件和卡片通过具体发送者接口或 `chat` 下的平台 API 发送

`Recallable` 提供 `boolean recall()`。当前实现该接口的消息类型为 `QQMessage`、`NapcatMessage`、`KookMessage`；`QQGuildMessage` 和 `DiscordMessage` 未提供该能力

```java
if (message instanceof Recallable recallable) {
    boolean recalled = recallable.recall();
}
```

消息对象保存撤回需要的平台与会话上下文，调用时直接交给对应平台 API。消息 ID 仍是字符串，返回值表示平台调用结果，操作仍受平台权限限制

QQ 官方群聊和 C2C 提供同步与异步撤回入口：

| 场景 | 同步方法 | 异步方法 |
| --- | --- | --- |
| 群聊 | `GroupChat.recallMessage(groupOpenId, messageId)` | `AsyncGroupChat.recallMessage(groupOpenId, messageId)` |
| C2C | `C2CChat.recallMessage(openId, messageId)` | `AsyncC2CChat.recallMessage(openId, messageId)` |

同步方法等待结果并返回 `boolean`，异步方法返回 `CompletableFuture<Boolean>`。HTTP 状态为 2xx 时结果为 `true`，接口请求失败为 `false`；任务被拒绝或执行异常时，异步结果可能异常完成，调用方应处理异常

这些接口与发送共用 `ThreadManager`。在其管理的任务中嵌套调用时，请求直接在当前工作线程执行，避免同步包装等待子任务导致死锁，因此异步入口不保证每次都切换线程。同步方法和 `message.recall()` 仍会等待平台响应

## QQ 官方机器人

使用 `qq.enabled` 启用，配置 `app-id`、`client-secret`、`api-base-url`

- `connection-mode: ws` 使用 WebSocket
- `connection-mode: webhook` 使用 Webhook，默认路径 `/qq/webhook`
- 内置模板的 API 地址是沙箱地址，部署时根据所用环境配置

适配代码位于 [platform/qq](../../src/main/java/top/yzljc/atribot/platform/qq)，消息 API 位于 [chat/official](../../src/main/java/top/yzljc/atribot/chat/official)。群聊、C2C、频道与频道私信事件分别处理

### 频道帖子

帖子模型位于 [thread](../../src/main/java/top/yzljc/atribot/chat/official/thread)，发送入口为 [GuildChannelChat](../../src/main/java/top/yzljc/atribot/chat/official/GuildChannelChat.java) 和 [AsyncGuildChannelChat](../../src/main/java/top/yzljc/atribot/chat/official/AsyncGuildChannelChat.java)。`channelId` 使用目标子频道板块 ID

#### 富文本构建

[thread](../../src/main/java/top/yzljc/atribot/chat/official/thread) 提供 [RichText](https://bot.q.qq.com/wiki/develop/api-v2/server-inter/channel/content/forum/model.html#RichText) 的 Java 封装：

```java
import top.yzljc.atribot.chat.official.thread.*;

RichText content = new RichText()
        .paragraph(new Paragraph().alignment(Alignment.MIDDLE).text("公告", TextStyle.BOLD))
        .blankLine()
        .paragraph(new Paragraph().text("正文").url("https://example.com", "详情"))
        .paragraph(new Paragraph().image("https://example.com/image.png"));

GuildThread thread = new GuildThread("标题", content);
Object body = thread.toObject();
```

`Paragraph` 支持文本、图片、视频、链接、@ 用户和对齐；文本样式可组合加粗、斜体、下划线。`Elem` 可单独创建并通过 `Paragraph.add(...)` 添加。空段落表示空行。`Paragraph.atUser(userId, userName)` 或 `Elem.atUser(...)` 创建 `type=6`、`at_type=1` 的用户提及元素，用户 ID 保留为字符串

`GuildThread(title, content)` 在构造时将正文保存为 JSON 字符串，并设置 `FORMAT_JSON`；后续修改构建对象不改变已创建的帖子。原有 `GuildThread(title, String, Format)` 构造继续可用

`Format` 提供 `FORMAT_TEXT`、`FORMAT_HTML`、`FORMAT_MARKDOWN`、`FORMAT_JSON`。例如普通文本帖子可使用 `new GuildThread("标题", "正文", Format.FORMAT_TEXT)`

`GuildThread` 实现 [JsonPayload](../../src/main/java/top/yzljc/atribot/utils/JsonPayload.java)，`toObject()` 返回包含 `title`、`content`、`format` 的 Map。发送层用 Jackson 将整个 Map 序列化为请求 JSON；其中 `content` 按接口格式保留为字符串，富文本正文中的引号由 Jackson 转义

#### 富文本解析

接口返回的标题和正文可能是包含富文本 JSON 的字符串，解析入口同时接受字符串、对象节点和字符串节点：

```java
RichText title = RichText.fromJson(response.path("title"));
RichText content = RichText.fromJson(response.path("content"));
RichText converted = objectMapper.treeToValue(response.path("content"), RichText.class);
```

解析保留元素顺序、空段落、缺省或空的属性对象，以及未识别的元素和扩展字段。通过 `getParagraphs()`、`getElems()` 遍历，通过 `Elem.getType()` 判断类型。再次序列化可使用 `toJson()`、`toObject()` 或 Jackson；格式无效时解析失败，不返回空正文替代原文

#### 发表与删除

同步调用：

```java
String taskId = GuildChannelChat.createThread(channelId, thread);
boolean deleted = GuildChannelChat.deleteThread(channelId, threadId);
```

异步调用：

```java
CompletableFuture<String> creation = AsyncGuildChannelChat.createThread(channelId, thread);
CompletableFuture<Boolean> deletion = AsyncGuildChannelChat.deleteThread(channelId, threadId);
```

以上使用 `chat.official` 下的发送类；异步结果类型来自 `java.util.concurrent.CompletableFuture`

| 操作 | 请求 | 返回值 |
| --- | --- | --- |
| 发表 | `PUT /channels/{channelId}/threads` | 响应中的 `task_id`，未取得任务 ID 时为 `null` |
| 删除 | `DELETE /channels/{channelId}/threads/{threadId}` | HTTP 2xx 为 `true`，空响应体也视为成功；接口请求失败为 `false` |

**`task_id` 是发表任务 ID，不能用于删除帖子**。删除需要实际帖子 ID，例如 [帖子事件](events.md#officialatforumthreadcreateevent) 中的 `event.getThreadInfo().threadId()`。取得任务 ID 仅表示接口返回了发表任务，不表示已取得帖子 ID

同步方法等待异步结果；等待中断或任务异常时，发表返回 `null`，删除返回 `false`，中断标记会恢复。异步接口沿用前述 `ThreadManager` 调度规则，调用方应同时处理正常结果和异常完成

发表和删除均接入 WebUI 发送日志，场景分别为「频道帖子发布」和「频道帖子删除」，记录请求方法、地址、响应状态与结果；发表还记录请求正文。紧急暂停期间，发表返回 `null`，删除仍可执行

## Napcat / OneBot

`napcat.server-url` 指向 Napcat HTTP API。Napcat 的事件上报地址指向 AtriMeow 的 `POST /`，端口为 `listen-port`

`bot-uin` 为机器人 QQ 号，`admin-uins` 为配置的管理员列表。群功能配置保存在 `groupconfig.json`

协议入口位于 [platform/napcat](../../src/main/java/top/yzljc/atribot/platform/napcat)，消息 API 位于 [chat/napcat](../../src/main/java/top/yzljc/atribot/chat/napcat)

## Discord

配置 `discord.enabled`、`bot-token`、`api-base-url` 与 `intents`。连接和交互处理位于 [platform/discord](../../src/main/java/top/yzljc/atribot/platform/discord)

当前命令入口为结构化 Slash Command，参数声明来自 `atribot.yml`，由 `SlashCommandExecutor` 处理，详见 [指令](commands.md)

## KOOK

直接接入 Webhook 与 HTTP API v3，无 KOOK SDK 依赖：

```yaml
kook:
  enabled: true
  bot-token: "替换为 Bot Token"
  verify-token: "替换为 Verify Token"
  encrypt-key: ""
  api-base-url: "https://www.kookapp.cn/api/v3"
  webhook-path: "/kook/webhook"
  admin-ids: []
```

Callback URL 指向外部地址的 `/kook/webhook`。路径须位于 `/kook/` 下且不得与其他回调重复。支持 zlib 压缩，回调 URL 也可附加 `?compress=0`

`bot-token` 用于出站 API，`verify-token` 用于回调校验。启用加密时，`encrypt-key` 必须与平台配置一致；启用后拒绝未加密回调

当前使用文本指令，KOOK 频道和私信支持以下 16 个主指令（别名不重复计数）：

| 分类 | 指令 |
| --- | --- |
| 帮助与状态 | `help`、`whoami`、`ping` |
| Hypixel | `hyp`、`hypstatus`、`wz`、`zs`、`bantrack`、`skbpack` |
| Minecraft | `mctool`、`mcv`、`mccape`、`mojang` |
| 日常查询 | `today`、`newyear`、`hitokoto` |

`hyp` 保留通用与 SkyBlock 分类，支持全部 16 个子指令。查询玩家时显式填写玩家名或 UUID；KOOK 用户尚未绑定 AtriAccount，不使用 QQ OpenID 查询账号。山之心、树心查询可通过返回的指令切换存档；物品价格分页使用 `/hyp ip <物品> --cursor <游标>`

`mctool` 支持 `ver`、`cape`、`lb`、`pack`。QQ 专属签到、抽卡、群管理、推送配置及提醒功能未开放给 KOOK

动态图片生成与查询传入发送者的 `Platform`。KOOK 使用响应中的 `api_url`，经 API 下载后上传至 KOOK；缺少有效 API 地址时返回错误，不回退到 COS。官机 QQ 仍遵循原有分发方式

### 卡片

```java
if (sender instanceof KookCommandSender kook) {
    kook.sendCard(new KookCard()
            .theme(KookTheme.SUCCESS)
            .header("查询结果")
            .markdown("**玩家：** Steve")
            .divider()
            .buttons(
                    KookButton.link("详情", "https://example.com/player/Steve"),
                    KookButton.callback("刷新", "refresh:Steve")
            ));
}
```

组件位于 [chat/kook](../../src/main/java/top/yzljc/atribot/chat/kook)。图片沿用 `ImageComponent`：

```java
kook.sendMessage(ImageComponent.imageOf("https://images.example.com/image.png"));
kook.sendMessage(ImageComponent.imageOf(base64, ImageType.BASE64).setText("图片说明"));
```

URL 图片在内部下载并上传到 KOOK，Base64 数据解码后上传，两种方式均使用 `type=2` 发送纯图片消息，返回图片消息 ID。组件附带的文字另发纯文本消息。远程下载上限为 32 MiB，不携带机器人凭据。文件使用 `sendFile(Path)` 上传发送。按钮回调通过 `KookButtonClickEvent` 处理，按钮值不会自动执行为命令

### 事件投递

回调完成校验后进入异步处理，未就绪或容量不足时返回 `503`。队列最多容纳 256 个未处理完成的事件；已完成事件去重记录最多 20,000 条，保留 10 分钟

队列与去重记录均在内存中，不保证跨重启投递。出站发送不自动重试，避免重复产生消息。实现见 [platform/kook](../../src/main/java/top/yzljc/atribot/platform/kook)
