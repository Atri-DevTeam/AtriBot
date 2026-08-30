# AtriMeow - 亚托利喵机器人

基于 Java/Kotlin 的多平台机器人，同时适配 **Napcat（OneBot 协议）**、**QQ 官方机器人**、**QQ 频道（Tencent Guild）** 与 **Discord**。注意：本项目的一些写法与 `Bukkit` 高度相似。

> 免责声明：撰写者是个什么都不会的新手，且部分复杂逻辑使用了 AIGC，说人话就是这是屎山，仅部分内容能参考一下（虽然但是，我的屎山能跑！！！）

> 为什么有些地方使用了 Kotlin 呢？因为作者想学着写，这个项目本质上是一个练手作品，所以有些稀烂的地方恳请理解。

可以支持一下我们的机器人喵 `亚托利喵` `UIN: 3889798968`。

## 目录

- [平台架构](#平台架构)
- [快速开始](#快速开始)
- [配置文件](#配置文件)
- [消息发送](#消息发送)
  - [Napcat（OneBot）](#napcatonebot)
  - [官方机器人](#官方机器人)
- [事件系统](#事件系统)
- [指令系统](#指令系统)
- [延迟执行与异步任务](#延迟执行与异步任务)
- [AI 服务](#ai-服务)
- [功能模块](#功能模块)
- [Napcat 群组功能开关](#napcat-群组功能开关)
- [WebUI](#webui)
- [公开 API](#公开-api)
- [构建与运行](#构建与运行)
- [CI/CD](#cicd)
- [项目结构](#项目结构)
- [License](#license)

---

## 平台架构

项目同时运行四套适配层，共享同一套事件系统和指令系统：

| 层级 | Napcat（OneBot） | QQ 官方机器人 | QQ 频道（Guild） | Discord |
|------|------------------|---------------|------------------|---------|
| 入口 | HTTP POST `127.0.0.1:port/` | WebSocket 网关或 HTTPS Webhook | `tencent-channel` CLI 进程 | WebSocket 网关连接 |
| 消息发送 | `chat/napcat/` | `chat/official/` | `sakuraba_ema/` | `chat/discord/` |
| 消息类型 | 文本、图片(URL/BASE64/FILE)、@、回复、合并转发 | 文本、Markdown、图片、文件、键盘按钮、Ark、Embed、媒体、流式 | 文本子频道、帖子、流式 | 文本、Embed、Components、Slash Commands |
| 事件类 | `Napcat*Event` | `Official*Event` | `sakuraba_ema` 内部回调 | `Discord*Event` |
| 功能类 | `function/napcat/` | `function/official/` | `function/general/` 部分 | `function/general/` 部分 |
| 平台枚举 | `NAPCAT_GROUP` / `NAPCAT_PRIVATE` | `OFFICIAL_GROUP` / `OFFICIAL_C2C` | `OFFICIAL_GUILD_CHANNEL` / `OFFICIAL_GUILD_DM` | `DISCORD_GUILD` / `DISCORD_DM` |

- `Napcat` 的网络接口是 `HTTP` 模式
- `QQ 官方机器人` 默认通过 WebSocket 网关连接，也支持通过 `qq.connection-mode: webhook` 使用 HTTPS Webhook
- `QQ 频道` 通过 `ChannelCliClient` 调用本机 CLI 程序，每次按需启停进程

四个平台通过 `CommandManager` 统一处理指令，通过 `EventManager` 统一派发事件。

---

## 快速开始

### 环境要求

- JDK 25+（构建工具链指定 Java 25，开启 `--enable-preview`）
- MySQL 数据库
- 视启用的平台而定：Napcat 服务端（OneBot 11）、QQ 开放平台 AppID/Secret、QQ 频道 CLI、Discord Bot Token

### 运行

1. 将 `src/main/resources/config.yml` 复制到 jar 同级目录，按需填写（见下方[配置文件](#配置文件)）
2. `java -jar AtriMeow.jar`
3. 控制台可输入 `stop` / `reboot` / `webui` / `groupinfo` 等指令（详见 `HelpCommand`）
4. WebUI 默认不开，正式环境需设置 `env: "production"` 并通过 `/webui` 指令或控制台 `webui` 开启

---

## 配置文件

配置文件为 jar 同级目录下的 `config.yml`，首次运行会自动从 jar 内模板复制一份带默认值的文件到工作目录。

### 全局设置

```yaml
command-prefix: "/"                # 指令前缀，所有平台通用
debug-command-suffix: "--debug"   # Debug 命令后缀（搭配 sender.isDebug()）
ttf-file-name: "default.ttf"      # 根目录字体文件名（默认鸿蒙开源字体）
debug-mode: false                  # Debug 模式（部分功能行为变化）
listen-port: 1234                  # HTTP 服务器监听端口（WebUI、Napcat 回调）
api-url: null                      # 外部 API 根地址
ugc-api-url: null                  # UGC/生图端 API 根地址
env: "dev"                         # dev / production；dev 才会自动启动 WebUI

mysql:                             # MySQL 数据库连接（必填）
  host: "localhost"
  port: 3306
  database: "database"
  username: "root"
  password: "null"

delivery:
  oss-dump-base-url: "null"        # 投递 OSS 转储地址
```

### AI 服务（多提供商）

AI 服务支持多提供商配置，按 `AiProvider` 枚举区分。`ai.<configKey>` 下的每个子节点对应一个独立的提供商；除 `api-key / base-url / model / timeout` 之外的任意字段（如 `temperature`、`top_p`、`max_tokens`、`extra_body`）都会原样合并到请求 JSON body 中：

```yaml
ai:
  default:
    api-key: ""
    base-url: ""                                  # OpenAI 兼容的 /v1/chat/completions
    model: "qwen3.5-flash"
    timeout: 30000000                             # 毫秒
  # 其他 provider（如 "other"、"opencode" 等）可继续在此追加，configKey 与枚举名一致
  # temperature: 1
  # top_p: 0.95
  # max_tokens: 16384
  # extra_body:
  #   chat_template_kwargs:
  #     thinking: false
```

代码中通过 `AiProvider` 枚举（`DEFAULT`、`OTHER`、`OPENCODE` 等）选择提供商。

### Napcat 适配器

```yaml
napcat:
  enabled: false                              # 是否启用 Napcat 适配器
  server-url: "http://0.0.0.0:12345"          # Napcat HTTP 上报地址
  debug-group-uin: 123456789                  # Debug 群号（用于日志/通知转发）
  bot-uin: 123456789                          # Bot 自身 QQ 号
  admin-uins:                                 # 管理员 QQ 号列表
    - "123456789"
  message-spy-groups:                         # 消息监听群列表（@ 消息和私聊会转发到 debug 群）
    - "123456"
  recall-ignore-user:                         # 撤回监听屏蔽用户
    - "123456"
```

### 官方机器人

```yaml
qq:
  enabled: false
  app-id: ""                                  # QQ 开放平台 AppID
  client-secret: ""                           # QQ 开放平台密钥
  api-base-url: "https://sandbox.api.sgroup.qq.com"  # 沙箱/正式环境
  connection-mode: "ws"                      # ws（默认）、webhook（也可写 wh）
  webhook-path: "/qq/webhook"                 # Webhook 回调路径
  groups:                                     # 可选：额外的群聊专用 Bot，可配置多个实例
    secondary:                                # 实例 key，仅允许字母、数字、_、-
      enabled: false
      app-id: ""
      client-secret: ""
      api-base-url: "https://sandbox.api.sgroup.qq.com"
      webhook-path: "/qq/groups/secondary/webhook"
  official-webui-token: "your-random-token"   # WebUI 登录 Token（自行生成）
  official-openId: ""                         # Bot 自身 OpenId（启动时由 /users/@me 自动拉取）
  official-username: ""                       # 兼容字段（已弃用）
  debug-group-openId: ""                      # Debug 群 OpenId
  super_admin_id: "null"                      # 超级管理员用户 OpenId
```

`qq.groups` 下每个启用项都是独立实例，只使用 HTTPS Webhook，并且只接收群聊域事件；不会接收 C2C、频道消息或频道私信。实例各自持有 AppID、Client Secret、access token 缓存和消息序号缓存，但复用现有 `OfficialGroup*Event`、`EventManager` 与指令系统。群事件触发的回复、图片、按钮、撤回、禁言及入群审批会按群 OpenID 自动路由回收到该事件的实例。

生产环境可用环境变量覆盖凭据，变量名格式为 `QQ_GROUP_BOT_<KEY>_APP_ID` 与 `QQ_GROUP_BOT_<KEY>_CLIENT_SECRET`；例如 `secondary` 对应 `QQ_GROUP_BOT_SECONDARY_APP_ID`。每个启用实例的 AppID 和 Webhook 路径都必须唯一。

### QQ 频道 CLI

```yaml
tencent-channel:
  enabled: false
  cli-path: ""                                # Linux 建议填写原生二进制绝对路径；Windows 可填写 .exe 或 .cmd
  login-token: "bot:v1_xxx"                   # 也可通过 TENCENT_CHANNEL_LOGIN_TOKEN 环境变量覆盖
  timeout-seconds: 90                         # 单次 CLI 调用超时
```

### Discord 机器人

```yaml
discord:
  enabled: false
  bot-token: "MTxxxxxxxxxxxxx.Gxxxxx.xxxxxxx" # 不要带 "Bot " 前缀
  api-base-url: "https://discord.com/api/v10"
  intents: 37377                              # 默认包含 GUILDS / GUILD_MESSAGES / DIRECT_MESSAGES / MESSAGE_CONTENT
```

### 功能配置

```yaml
atribot-key-secret: "null"                    # AtriBot Key Secret（统一身份校验）

function:
  us-api: "http://us-api.yzljc.top:10775"     # 上游 US API
  hypixel-reward-ws: "ws://localhost:8765"     # Hypixel 奖励 WebSocket
  sa-sign-key: "null"                          # SA 签到密钥
  github-webhook:                             # GitHub Webhook
    port: 54321
    secret: "your-secret"
  bilibili-cookie: ""                          # B 站视频解析 Cookie
  wakeup-image-link: ""                        # 叫醒表情包链接
  keywords-hitokoto:                          # 触发"一言"的关键词
    - "一言"
    - "hitokoto"
    - "yiyan"
  keywords-like-user:                         # 触发"点赞"的关键词
    - "点赞"
    - "likeme"
    - "zanwo"
    - "赞我"

verify:                                       # Minecraft 验证服务器
  enabled: false
  port: 8080
  host: "127.0.0.1"
  key: "public-key"                            # RSA 公钥
```

通过 Minecraft Socket 实现账号验证，玩家在游戏内输入验证码即可绑定 MC 账号。

### IMAP 邮件监听

```yaml
email:
  enabled: false
  username: ""
  password: ""
  protocol: "imap"
  host: "imap.qq.com"
  port: 993
  ssl-enabled: true
  connection-timeout: 30000
  read-timeout: 300000
```

启用后会监听邮箱新邮件并触发 `EmailMessageEvent`（默认由 `function/napcat/personal/EmailNotify` 转发给机器人主人）。

### 统一身份验证（UA）

```yaml
ua:
  verify:
    strategy-id: "st_xxx"                    # 统一身份验证默认策略
```

### 其他配置

```yaml
# 特殊群推送
manosaba-group-id: 0

# 加群验证
group-join-verify-group-id: 123456789
group-join-verify-message: "欢迎加入，请在30秒内发送验证消息"
group-join-verify-timeout-seconds: 15
group-join-verify-answer: "验证"

# 官方机器人活跃消息群列表（已弃用）
official-active-message-groups:
  - "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# 云大陆（Yunland）连接
yunland:
  host: "127.0.0.1"
  port: 12345
  connect-key: "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8..."
```

### 图源投稿

```yaml
image-source:
  enabled: false
  # 投稿上报地址，远端响应与 Result 同构；status=200 成功，400 失败
  upload-url: "https://qq-ugc.yzljc.top/v2/imagesource/upload"
  # 图片浏览地址前缀，WebUI 用 <view-base-url>/<uuid> 展示图片
  view-base-url: "https://qq-ugc.yzljc.top/v2/imagesource/get"
  # 投稿删除地址
  delete-url: "https://qq-ugc.yzljc.top/v2/imagesource/delete"
  # 共享密钥。投稿上报给远端图床时作为 Authorization: Bearer <token> 发送
  token: "null"
  # 单个用户同时处于未审核状态的投稿数量上限
  pending-limit: 3
```

### 抽卡（Loots）

```yaml
loots:
  # 抽卡目录管理密钥，需与 atrimeow-ugc 部署配置中 atri.loots.admin-token 保持一致
  admin-token: "atri-loots-admin@2026"
```

---

## 消息发送

### Napcat（OneBot）

Napcat 消息发送统一使用 `GroupMessage`（群聊）和 `PrivateMessage`（私聊）两个门面类，底层由 `MessageUtils` 实现。**所有发送方法返回 `messageId`（成功）或 `null`（失败）**。图片统一使用 `ImageComponent` 封装，可通过 `ImageComponent.imageOf(...)` 或 `new ImageComponent(data, ImageType.URL/BASE64)` 构造。

#### 群聊消息

```java
// 发送纯文本
GroupMessage.chatMessage(groupId, "你好世界");

// 发送文本并 @ 用户
GroupMessage.chatMessage(userId, groupId, "你好", true);

// 发送图片（三种来源：URL / BASE64 / FILE）
GroupMessage.chatMessage(groupId, ImageComponent.imageOf("https://example.com/img.png"));
GroupMessage.chatMessage(groupId, new ImageComponent("base64...", ImageType.BASE64));
GroupMessage.chatMessage(groupId, new ImageComponent("/path/to/img.png", ImageType.FILE));

// 发送文本+图片
ImageComponent img = new ImageComponent("https://example.com/img.png").setText("看这张图");
GroupMessage.chatMessage(groupId, img);

// 回复消息
GroupMessage.replyMessage(groupId, messageId, "回复内容");
GroupMessage.replyMessage(groupId, messageId, ImageComponent.imageOf("https://example.com/img.png"));
GroupMessage.replyMessage(userId, groupId, messageId, true, "@该用户 + 回复内容");

// @ 某人
GroupMessage.atUser(userId, groupId, "提醒你");

// 转发单条消息
GroupMessage.forwardTo(groupId, messageId);

// 撤回消息
GroupMessage.recallMessage(messageId);
```

#### 私聊消息

```java
// 发送文本
PrivateMessage.chatMessage(userId, "你好");

// 发送图片
PrivateMessage.chatMessage(userId, ImageComponent.imageOf("https://example.com/img.png"));

// 回复消息
PrivateMessage.replyMessage(userId, messageId, "回复内容");
PrivateMessage.replyMessage(userId, messageId, ImageComponent.imageOf("https://example.com/img.png"));

// 撤回
PrivateMessage.recallMessage(messageId);
```

#### 合并转发（群聊 / 私聊均可）

```java
// 构造转发节点
List<MessageSegment> nodes = List.of(
    GroupMessage.createTextNode("第一条消息"),
    GroupMessage.createImageNode("https://example.com/img.png"),
    GroupMessage.createTextNode("第三条", "uin", "展示昵称")
);

// 发送合并转发
GroupMessage.forwardMessage(groupId, nodes, "标题", "摘要", "文字变量...");
PrivateMessage.forwardMessage(userId, nodes, "标题", "摘要");
```

#### 消息段类型

Napcat 使用 `MessageSegment(String type, Map<String, Object> data)` 表示消息段（`record` 类型）：

| type | data 参数 | 说明 |
|------|-----------|------|
| `text` | `{"text": "..."}` | 文本 |
| `image` | `{"url": "..."}` 或 `{"file": "base64://..."}` 或 `{"file": "file://..."}` | 图片 |
| `at` | `{"qq": "123456"}` | @某人 |
| `reply` | `{"id": "msgId"}` | 引用回复 |
| `node` | `{"uin": "...", "name": "...", "content": [...]}` | 转发节点 |

---

### 官方机器人

官方机器人使用 `GroupChat`（群聊）和 `C2CChat`（私聊）两个静态门面类，内部委托 `AsyncGroupChat` / `AsyncC2CChat` 异步执行，**支持文本、Markdown、图片、文件、键盘按钮、富媒体、流式** 等消息类型。另外 `GuildChannelChat`（文字子频道）和 `GuildDirectChat`（频道私信）由 `sakuraba_ema` 提供支持。

#### 群聊消息

```java
// 发送纯文本
GroupChat.sendMessage(groupOpenId, "你好世界");

// 发送 Markdown（使用 TC.md() 快捷构造）
GroupChat.sendMessage(groupOpenId, TC.md("**加粗文本**\n# 标题"));

// 发送 Markdown 带图片
GroupChat.sendMessage(groupOpenId, TC.md(
    "看图：" + Markdown.img("https://example.com/img.png", 800, 600)
));

// Markdown 中 @ 用户
GroupChat.sendMessage(groupOpenId, TC.md(
    Markdown.at(userOpenId) + " 你好"
));

// Markdown 中插入可点击命令
GroupChat.sendMessage(groupOpenId, TC.md(
    "点击这里：" + Markdown.enterCommand("/打卡", "点我打卡")
));

// 发送 Markdown 带键盘按钮（最大支持 5x5）
Object keyboard = TC.keyboard(List.of(
    List.of(
        new Button("btn1", "打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
        new Button("btn2", "帮助", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND)
    )
));
GroupChat.sendMessage(groupOpenId, TC.md("请选择操作："), keyboard);

// 发送图片（使用 ImageComponent）
GroupChat.sendMessage(groupOpenId, ImageComponent.imageOf("https://example.com/img.png"));
GroupChat.sendMessage(groupOpenId, new ImageComponent("base64...", ImageType.BASE64));

// 回复文本消息
GroupChat.replyMessage(groupOpenId, msgId, "回复文本");

// 回复 Markdown（会自动 @ 原消息发送者）
GroupChat.replyMessage(groupOpenId, userOpenId, msgId, TC.md("回复的 **Markdown**"));

// 回复 Markdown 带键盘
GroupChat.replyMessage(groupOpenId, userOpenId, msgId, TC.md("选一个："), keyboard);

// 回复图片
GroupChat.replyMessage(groupOpenId, msgId, ImageComponent.imageOf("https://example.com/img.png"));

// 回复文件（FileType.IMAGE / VIDEO / AUDIO / FILE）
GroupChat.replyMessage(groupOpenId, msgId, FileType.FILE, "fileUrl");

// 引用回复（refMessage，传入 ref_idx）
GroupChat.refMessage(groupOpenId, refIdx, "引用内容");

// 群事件回复（Markdown / 文本 / 图片 / 键盘）
GroupChat.replyEventMessage(groupOpenId, eventId, TC.md("event reply"));
GroupChat.replyEventMessage(groupOpenId, memberOpenId, eventId, TC.md("event reply with @"));
GroupChat.replyEventMessage(groupOpenId, eventId, TC.md("event reply"), keyboard);

// 撤回消息
GroupChat.recallMessage(groupOpenId, messageId);
```

#### 私聊（C2C）消息

```java
// 发送文本
C2CChat.sendMessage(openId, "你好");

// 发送 Markdown
C2CChat.sendMessage(openId, TC.md("**你好**，这是 Markdown"));

// 发送 Markdown 带键盘
C2CChat.sendMessage(openId, TC.md("请选择："), keyboard);

// 发送图片
C2CChat.sendMessage(openId, ImageComponent.imageOf("https://example.com/img.png"));

// 回复消息（文本/Markdown/图片/键盘）
C2CChat.replyMessage(openId, msgId, "回复文本");
C2CChat.replyMessage(openId, msgId, TC.md("**回复** Markdown"));
C2CChat.replyMessage(openId, msgId, ImageComponent.imageOf("https://example.com/img.png"));
C2CChat.replyMessage(openId, msgId, TC.md("选一个："), keyboard);

// 引用回复
C2CChat.refMessage(openId, refIdx, "引用内容");

// 私聊事件回复
C2CChat.replyEventMessage(openId, eventId, TC.md("event reply"));

// 流式消息（增量更新）
C2CChat.streamDeltas(openId, List.of(TC.md("第一段"), TC.md("第二段")));
C2CChat.replyStreamDeltas(openId, msgId, List.of(TC.md("增量1"), TC.md("增量2")));
C2CChat.streamTextDeltas(openId, List.of("文本1", "文本2"));
C2CChat.replyTextStreamDeltas(openId, msgId, List.of("增量1", "增量2"));

// 撤回
C2CChat.recallMessage(openId, messageId);
```

#### QQ 频道文字子频道 / 频道私信

```java
// 文字子频道被动回复
GuildChannelChat.replyMessage(channelId, msgId, "回复内容");
GuildChannelChat.replyMessage(channelId, msgId, ImageComponent.imageOf("https://example.com/img.png"));

// 频道私信被动回复
GuildDirectChat.replyMessage(guildId, msgId, "回复内容");
GuildDirectChat.replyImageMessage(guildId, msgId, ImageComponent.imageOf("https://example.com/img.png"));
```

> 频道方向上，绝大部分业务逻辑（`sakuraba_ema` 调用 CLI）由 `top.yzljc.sakuraba_ema.ChannelCliClient` 统一封装，包括发帖、回帖、转发、论坛渲染等（`ChannelPosts`、`ForumCode` 等）。

#### 官方机器人消息类型总结

| 类型 | 枚举/类 | 说明 |
|------|---------|------|
| 文本 | `GroupMessageType.TEXT(0)` | 普通文本消息 |
| Markdown | `GroupMessageType.MARKDOWN(2)` | 支持 QQ 官方 Markdown 语法（加粗、标题、链接、图片、@、命令按钮等） |
| 媒体(图片/视频/语音/文件) | `GroupMessageType.MEDIA(7)` | 需先上传文件，再发送 media 消息，`FileType` 区分具体类型 |
| ARK | `GroupMessageType.ARK(3)` | ARK 模板消息（几乎不可用） |
| Embed | `GroupMessageType.EMBED(4)` | Embed 消息 |

#### Markdown 辅助方法（`Markdown` 类）

```java
Markdown.img("https://...", 800, 600)                            // 嵌入图片
Markdown.img("替代文本", "https://...", 800, 600)                 // 带替代文本的图片
Markdown.at(userOpenId)                                           // @用户
Markdown.atAll()                                                  // @全体成员（<qqbot-at-everyone />，群聊会 40034106）
Markdown.enterCommand("/打卡")                                    // 可点击的命令
Markdown.enterCommand("/打卡", "点我打卡")                         // 自定义显示文字
Markdown.enterCommand("/打卡", "点我打卡", true)                   // 指定是否引用
Markdown.link("https://...", "链接文字")                          // 超链接
Markdown.colored(HexColor.RED, "红色文字")                        // $\textcolor{...}{...}$ 语法
```

#### 按钮（`Button` 类）

```java
new Button(
    "buttonId",           // 按钮唯一标识
    "显示文字",            // displayText
    "/command data",      // data（回传数据）
    true,                 // enter（点击是否自动发送命令）
    ButtonStyle.BLUE,     // 样式：GRAY(0) / BLUE(1) / ICON_BUTTON(2) / RED(3) / BLUE_WITH_BACKGROUND(4)
    ButtonType.COMMAND    // 类型：LINK(0) / CALLBACK(1) / COMMAND(2)
).setReply(true)          // 可选：开启后回执消息会引用原消息
 .setVisitedDisplayText("已点击")   // 可选：点击后显示文字
 .setPermissionType(PermissionType.ALL)   // 可选：ALL / ADMIN / SPECIFIC_USER
 .setAllowedOpenIds(List.of("openid"))    // 可选：当 SPECIFIC_USER 时生效
 .setModal("内容", "确认", "取消")        // 可选：模态框
```

按钮回调会触发 `OfficialButtonInteractionEvent`，可通过 `event.getButtonId()` / `event.getButtonValue()` 获取回调数据。`PermissionType` 还可设置为 `SPECIFIC_USER`/`ADMIN` 以限制可见用户。`TC.keyboard` 支持 `ButtonSize.SMALL` 来整体缩放按钮。

#### 流式消息

流式消息（`stream*` 系列）允许分多次发送 Markdown 增量，最终用户侧会得到拼接后的完整内容。`AsyncGroupChat` 同样提供对应异步方法。

---

## 事件系统

事件系统是统一的消息/通知处理框架，四个平台的底层事件都会经过它。

### 核心类

| 类 | 路径 | 说明 |
|----|------|------|
| `EventManager` | `event/EventManager.java` | 单例，负责注册监听器和派发事件 |
| `EventHandler` | `event/EventHandler.java` | 注解，标记事件处理方法 |
| `Listener` | `event/Listener.java` | 标记接口，实现它的类可被注册 |
| `Event` | `event/Event.java` | 所有事件的抽象基类 |
| `Cancellable` | `event/Cancellable.java` | 可取消事件的接口 |
| `EventPriority` | `event/EventPriority.java` | 优先级：LOWEST → MONITOR（共 6 级） |

### 所有可用事件

#### Napcat 事件

| 事件类 | 触发场景 | 可取消 |
|--------|---------|--------|
| `NapcatGroupMessageEvent` | 收到群消息 | 否 |
| `NapcatPrivateMessageEvent` | 收到私聊消息 | 否 |
| `NapcatPokedEvent` | 被戳一戳 | 否 |
| `NapcatFriendRequestEvent` | 收到好友请求 | 否 |
| `NapcatGroupRequestEvent` | 收到加群请求 | 否 |
| `NapcatRecallMessageEvent` | 消息被撤回 | 否 |
| `NapcatGroupMemberChangeEvent` | 群成员变动 | 否 |

#### 官方机器人事件

| 事件类 | 触发场景 | 可取消 |
|--------|---------|--------|
| `OfficialGroupMessageCreateEvent` | 收到群消息（含 `isAtBot`） | 否 |
| `OfficialGroupAtMessageCreateEvent` | 收到群 @ 消息（开启全量消息后此事件不再接收） | 否 |
| `OfficialC2CMessageCreateEvent` | 收到私聊消息 | 否 |
| `OfficialButtonInteractionEvent` | 按钮点击等交互 | 是 |
| `OfficialGroupJoinEvent` | Bot 被加入群聊 | 否 |
| `OfficialGroupDelEvent` | Bot 被移出群聊 | 否 |
| `OfficialFriendAddEvent` | 被添加好友 | 否 |
| `OfficialFriendDelEvent` | 被删除好友 | 否 |
| `OfficialGroupMemberAddEvent` | 群新成员加入 | 否 |
| `OfficialGroupMemberRemoveEvent` | 群成员退出 | 否 |
| `OfficialGroupJoinRequestEvent` | 用户申请加群（含 `applySource` / `method` / `invitedBy` / `verifyMessage` / `verifyQAList`） | 否 |
| `OfficialGroupSendFailEvent` | 主动群消息推送失败 | 否 |
| `OfficialC2CSendFailEvent` | 主动 C2C 消息推送失败 | 否 |
| `OfficialC2CAuthorizeModifyEvent` | C2C 用户授权变更 | 否 |

#### 腾讯频道事件

频道方向上的事件由 `sakuraba_ema` 内部消化，**不**进入 `EventManager`。若需联动，可监听 `OfficialGuildAtMessageCreateEvent` / `OfficialGuildDirectMessageCreateEvent` 拿到群/频道维度的消息事件：

| 事件类 | 触发场景 | 可取消 |
|--------|---------|--------|
| `OfficialGuildAtMessageCreateEvent` | 频道子频道内 @ 消息 | 否 |
| `OfficialGuildDirectMessageCreateEvent` | 频道私信 | 否 |

> 这两个事件类与 `OfficialGroupAtMessageCreateEvent` / `OfficialC2CMessageCreateEvent` 互斥，平台枚举分别为 `OFFICIAL_GUILD_CHANNEL` / `OFFICIAL_GUILD_DM`。

#### Discord 事件

| 事件类 | 触发场景 | 可取消 |
|--------|---------|--------|
| `DiscordMessageCreateEvent` | 收到 Discord 消息 | 否 |
| `DiscordSlashCommandEvent` | 收到 Slash Command（带 `reply(...)`、`deferReply(...)` 等应答方法） | 否 |

#### 通用事件

| 事件类 | 触发场景 | 可取消 |
|--------|---------|--------|
| `UserRunCommandEvent` | 用户执行指令前 | **是** |
| `EmailMessageEvent` | IMAP 邮件监听器收到新邮件 | 否 |

### 如何监听事件

#### 1. 创建一个实现 `Listener` 的类

```java
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.EventPriority;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;

public class MyFeature implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGroupMessage(NapcatGroupMessageEvent event) {
        // 忽略 Bot 自己的消息
        if (event.getUser().isBot()) return;

        String content = event.getMessage().getContent();
        String groupId = event.getGroupId();
        String userId = event.getUser().getUserId();

        if (content.contains("你好")) {
            // 直接在事件上发送消息
            event.sendMessage("你好！" + userId);
        }
    }
}
```

#### 2. 在 `Atri.java` 的 `onEnable()` 中注册

```java
EventManager.getInstance().registerEvents(new MyFeature());
```

#### 3. 事件优先级与取消

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onRunCommand(UserRunCommandEvent event) {
    // ignoreCancelled = true：如果前一个低优先级处理器已取消事件，这里不执行
    if (isUserBanned(event.getSender().getUserId())) {
        event.setCancelled(true);  // 阻止后续处理器执行
    }
}
```

### 事件上的便捷方法

**NapcatGroupMessageEvent**：
```java
event.sendMessage("文本回复");                // 回复消息
event.sendMessage(listOfSegments);            // 回复消息段列表
event.recall();                               // 撤回消息
event.getUser();                              // 获取发送者
event.getMessage().getContent();              // 获取消息文本
event.getMessage().getSegments();             // 获取消息段列表
```

**OfficialGroupMessageCreateEvent**：
```java
event.sendMessage("文本");
event.sendMessage(TC.md("**Markdown**"));
event.sendMessage(TC.md("..."), keyboard);     // Markdown + 键盘
event.sendMessage(ImageComponent.imageOf("https://..."));
event.isAtBot();                                // 是否 @ 了机器人
event.shouldIgnore();                           // 黑名单/拉黑一键判定
```

**OfficialButtonInteractionEvent**（按钮回调）：
```java
event.getButtonId();                                // 获取按钮 ID
event.getButtonValue();                             // 获取按钮回传数据
event.replyMessage(TC.md("..."));                   // 根据 chatType 自动选择群聊或私聊回复
event.sendMessage("文本");                          // 主动消息形式
event.answer(AnswerCode.SUCCESS);                   // 回复交互确认（必须手动执行，否则客户端将判定超时）
event.setCancelled(true);                           // 取消后续业务处理
event.shouldIgnore();                               // 黑/白名单一键判定（命中后会自动 answer(FAIL)）
```

**DiscordSlashCommandEvent**：
```java
event.reply("Pong!");                               // 直接应答 Slash Command
event.reply(DiscordEmbed...);                       // 应答 Embed
event.getArgs();                                    // 解析后的参数对象（SlashCommandArguments）
```

---

## 指令系统

指令通过 `CommandManager` 统一管理，同时监听 Napcat、官方机器人、QQ 频道和 Discord 的消息事件。

### 如何注册指令

指令分为两步：**在 YAML 中声明** + **绑定执行器**。

#### 1. 在 `src/main/resources/atribot.yml` 中声明

```yaml
commands:
  mycommand:
    description: 我的自定义指令
    usage: /mycommand <参数>
    aliases:
      - mc
      - mycmd
```

#### 2. 实现 `CommandExecutor` 并绑定

```java
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandSender;

public class MyCommandExecutor implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // 对于官方群聊、私聊、第三方机器人的指令时，必须对不同平台的行为进行约束，
        // 避免抛出 UnsupportedOperationException
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;

        sender.sendMessage("你执行了 /" + label + "，参数：" + String.join(" ", args));
        return true; // return false 将发送默认的指令使用帮助
    }
}
```

#### 3. 在 `Atri.java` 的 `onEnable()` 中绑定执行器

```java
CommandManager.getCommand("mycommand").setExecutor(new MyCommandExecutor());
```

### Discord Slash Command

Discord 平台额外支持 Slash Command。只需在 `CommandExecutor` 同时实现 `SlashCommandExecutor` 接口，`DiscordManager` 启动时会把所有 `SlashCommandExecutor` 推送到 Discord `PUT /applications/{id}/commands`：

```java
public class MySlashCommand implements SlashCommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 同时也是普通文本命令的处理
        return true;
    }

    @Override
    public boolean onSlashCommand(DiscordCommandSender sender, Command command, String label, SlashCommandArguments args) {
        sender.sendEmbed(DiscordEmbed.simple().title("Pong!"));
        return true;
    }
}
```

#### 在 YAML 中声明参数（options）

在 `src/main/resources/atribot.yml` 的命令节点下加 `options:` 数组，bot 启动时会自动转译为 Discord JSON 推送给 Discord（命令对应 `CommandOptionDefinition`）。`type` 直接用 Discord 官方编号：

```yaml
commands:
  ban:
    description: 封禁用户
    options:
      - name: user
        type: 6              # USER
        description: 被封禁的用户
        required: true
      - name: reason
        type: 3              # STRING
        description: 封禁原因
      - name: duration
        type: 4              # INTEGER
        description: 封禁天数
        min_value: 1
        max_value: 365
      - name: notify
        type: 5              # BOOLEAN
        description: 是否通知
      - name: scope
        type: 3
        description: 范围
        choices:
          - name: 全服
            value: global
          - name: 仅本群
            value: local
```

支持的字段：

| 字段 | 适用 type | 说明 |
|---|---|---|
| `name` | 全部 | 必填，≤32 字符，匹配 Discord 命名规范（小写、数字、下划线、连字符） |
| `type` | 全部 | 必填，Discord 官方编号 (1-11) |
| `description` | 全部 | 缺省回退到 `name` |
| `required` | leaf (3-11) | 仅叶子类型生效；写在 sub_command/sub_command_group 上会被忽略并 warn |
| `choices` | STRING/INTEGER/NUMBER | 每个 choice 是 `{name, value}`，value 类型需与 option type 匹配；最多 25 个 |
| `min_value` / `max_value` | INTEGER/NUMBER | 数值范围 |
| `channel_types` | CHANNEL | Discord 频道类型编号数组 |
| `options` | SUB_COMMAND/SUB_COMMAND_GROUP | 嵌套子命令，结构相同；嵌套层级最多 2 层（sub_command_group → sub_command → leaf） |

Type 编号速查：1=SUB_COMMAND、2=SUB_COMMAND_GROUP、3=STRING、4=INTEGER、5=BOOLEAN、6=USER、7=CHANNEL、8=ROLE、9=MENTIONABLE、10=NUMBER、11=ATTACHMENT。

运行时通过 `SlashCommandArguments` 取值（`getString` / `getInteger` / `getLong` / `getNumber` / `getBoolean`，或 `getOption(name)` 拿 `Option(name, type, value, raw)` 原始节点）：

```java
@Override
public boolean onSlashCommand(DiscordCommandSender sender, Command command,
                              String label, SlashCommandArguments args) {
    String userId = args.getString("user");
    Integer days = args.getInteger("duration");
    Boolean notify = args.getBoolean("notify");
    // ...
    return true;
}
```


### CommandSender 常用方法

`CommandSender` 是统一的发送者接口；按平台分别有 `NapcatCommandSender` / `QQCommandSender` / `QQGuildCommandSender` / `DiscordCommandSender` / `ConsoleCommandSender`，各自暴露平台特有的方法。共有的方法：

```java
// sender 自动适配平台（Napcat 群/私聊、官方群/私聊、Discord 等）
sender.sendMessage("文本回复");              // 自动选择正确的发送方式
sender.hasPermission();                      // 是否为该平台管理员
sender.hasPermission("perm.key");            // 细粒度权限（官方/C2C 用户有效）
sender.getUserId();                          // 获取用户 ID
sender.getUsername();                        // 获取用户昵称
```

平台特有示例：
- `NapcatCommandSender.getGroupId()`、`isBot()`、`getRole()`、`sendMessage(ImageComponent)`、`recall()`
- `QQCommandSender.getPlatform()`、`getGroupId()`、`sendMessage(Markdown, keyboard, at)`、`sendStreamTextMessage(...)`、`sendStreamMarkdownMessage(...)`、`recall(messageId)`
- `QQGuildCommandSender.getGuildId()` / `getChannelId()`
- `DiscordCommandSender.sendEmbed(...)` / `sendComponents(...)` / `sendEphemeralMessage(...)` / `sendFile(...)`
- `ConsoleCommandSender.getMessage()` / `hasPermission() = true`

---

## 延迟执行与异步任务

项目提供四套工具来执行延迟和异步任务：`Scheduler`、`ThreadManager`、`TaskScheduler` 和 `RunScheduleTask`。

### Scheduler

`Scheduler` 是 `Atri` 实例持有的调度器（通过 `Atri.getInstance().getScheduler()` 获取），基于 `ScheduledExecutorService`，实际任务执行委托给 `ThreadManager` 的虚拟线程池。支持**防重叠**的定时任务，即如果上一次任务尚未结束，本次触发会被自动跳过。

```java
Scheduler scheduler = Atri.getInstance().getScheduler();

// 立即异步执行
scheduler.runTask(() -> {
    // 你的代码
});

// 延迟 5 秒后执行
scheduler.runTaskLater(() -> {
    // 你的代码
}, 5000L);

// 定时重复执行：初始延迟 1 秒，之后每 60 秒执行一次
ScheduledFuture<?> task = scheduler.runTaskTimer(() -> {
    // 你的定时任务
}, 1000L, 60000L);

// 取消定时任务
scheduler.cancelTask(task);
```

| 方法 | 说明 |
|------|------|
| `runTask(Runnable)` | 立即异步执行 |
| `runTaskAsynchronously(Runnable)` | 同 `runTask`（别名） |
| `runTaskLater(Runnable, long delayMillis)` | 延迟 `delayMillis` 毫秒后执行 |
| `runTaskLaterAsynchronously(Runnable, long delayMillis)` | 同 `runTaskLater`（别名） |
| `runTaskTimer(Runnable, long delayMillis, long periodMillis)` | 定时重复执行，**防重叠**（上次未结束则跳过） |
| `runTaskTimerAsynchronously(Runnable, long delayMillis, long periodMillis)` | 同 `runTaskTimer`（别名） |
| `cancelTask(ScheduledFuture<?>)` | 取消任务 |
| `shutdown()` | 关闭调度器 |

### ThreadManager

`ThreadManager` 是全局静态工具类，位于 `service/runtime/ThreadManager.java`，内部使用**虚拟线程**（Virtual Threads）执行任务，通过信号量控制最大并发数（默认 `max(16, min(128, CPU * 8))`，可通过 `-Datribot.thread.maxConcurrency` 调整）。适合需要 `CompletableFuture` 链式调用或不需要 `Atri` 实例持久的场景：

```java
// 直接异步执行（fire-and-forget）
ThreadManager.execute(() -> {
    // 你的代码
});

// 获取 Future 对象
Future<?> future = ThreadManager.setExecute(() -> {
    // 你的代码
});

// 返回 CompletableFuture，支持链式调用
CompletableFuture<String> cf = ThreadManager.supplyAsync(() -> {
    // 执行耗时操作
    return "结果";
});

cf.thenAccept(result -> {
    // 处理结果
});

// 延迟 3 秒后执行
ThreadManager.schedule(() -> {
    // 你的代码
}, 3, TimeUnit.SECONDS);

// 延迟执行并获取 ScheduledFuture（可取消）
ScheduledFuture<?> scheduled = ThreadManager.setSchedule(() -> {
    // 你的代码
}, 30, TimeUnit.SECONDS);
```

| 方法 | 说明 |
|------|------|
| `execute(Runnable)` | 异步执行，无返回值 |
| `setExecute(Runnable)` | 异步执行，返回 `Future<?>` |
| `submit(Callable<T>)` | 提交带返回值的任务，返回 `Future<T>` |
| `supplyAsync(Supplier<T>)` | 返回 `CompletableFuture<T>`，支持链式调用 |
| `schedule(Runnable, long delay, TimeUnit)` | 延迟执行 |
| `setSchedule(Runnable, long delay, TimeUnit)` | 延迟执行，返回 `ScheduledFuture<?>`（可取消） |
| `getExecutor()` | 取得底层 `ExecutorService` |
| `getMaxConcurrentTasks()` | 取得信号量上限 |

### TaskScheduler（声明式定时任务）

`TaskScheduler` 是基于注解的声明式调度系统，位于 `service/taskscheduler/`。`ScheduledTask` 接口要求返回 `TaskSchedule`，由 `DefaultTaskSchedule` 提供，支持 `daily / hourly / half_hour / a_quarter` 四种模式。启动时由 `TaskSchedulerRegistry.registerAll(taskScheduler)` 自动扫描并注册。

```java
public class HypixelAnnouncements implements ScheduledTask {
    @Override
    public TaskSchedule schedule() {
        return new DefaultTaskSchedule().setMode(ScheduleMode.hourly);
    }
}
```

### RunScheduleTask（基于 `service/timer/` 注解）

另一套声明式任务系统，使用 `@Schedule(time = "HH:mm:ss", type = ScheduleType.DAILY/HOURLY/HALF_HOURLY)` 注解静态方法，启动时由 `RunScheduleTask.runAllTasks()` 扫描并注册：

```java
public class LootFreeDrawCleanupTask {
    @Schedule(time = "23:55:00", type = ScheduleType.DAILY)
    public static void clearDailyFreeDrawRecord() {
        LootService.clearDailyFreeDrawRecord();
    }
}
```

### 使用建议

- **简单延迟/定时**：用 `Scheduler`，通过 `Atri.getInstance().getScheduler()` 获取
- **需要链式异步处理**：用 `ThreadManager.supplyAsync()` 获取 `CompletableFuture`
- **一次性 fire-and-forget**：用 `ThreadManager.execute()`
- **声明式定时任务（业务对象）**：实现 `ScheduledTask` 接口 + `TaskScheduler`
- **声明式定时任务（静态方法）**：用 `@Schedule` 注解 + `RunScheduleTask`
- 所有任务执行都有异常捕获和日志记录，不会因未捕获异常导致线程崩溃

---

## AI 服务

`AiService` 提供多提供商 AI 对话能力，内部使用 OpenAI 兼容的 Chat Completions API。`AiProvider` 枚举定义了全部可用的提供商名称（`DEFAULT`、`OTHER`、`OPENCODE` 等），新增 provider 只需要在 `AiProvider` 追加枚举项并在 `config.yml` 中添加对应节点。

```java
AiService aiService = Atri.getInstance().getAiService();

// 使用默认提供商
String reply = aiService.ask("你好");

// 指定提供商
String reply = aiService.ask(AiProvider.OTHER, "你好");

// 自定义 System Prompt
String reply = aiService.askWithSystemPrompt("你好", "你是一个助手");
```

默认 System Prompt 将 AI 人设为《ATRI -My Dear Moments-》中的机器人少女亚托莉，可通过 `askWithSystemPrompt(provider, userMessage, systemPrompt)` 自定义。返回值可使用 `AiService.isValidResponse(content)` 判定是否拿到有效回答。

---

## 功能模块

### 通用功能（`function/general/`）

所有平台均可使用的功能：

| 类 | 功能 | 触发方式 |
|----|------|----------|
| `HelpCommand` | 帮助菜单 | `/help` |
| `SponsorCommand` | 贡献名单 | `/贡献名单` |
| `Hitokoto` | 随机一言 | `/hitokoto` 或关键词触发 |
| `MojangStatus` | Mojang 服务状态 | `/mojang` |
| `HappyNewYear` | 新年倒计时 | `/newyear` |
| `HypixelReward` | 领取 Hypixel 奖励（WebSocket 会话） | `/cl` |
| `HypixelStatus` | Hypixel 服务器状态 | `/hypstatus` |
| `HypixelAnnouncements` | Hypixel 公告检查 | `/check-hyp` |
| `HypixelAlphaForums` | Hypixel Alpha 论坛 | `/check-hyp-alpha` |
| `MinecraftCommand` | MC 子命令（dice / version / capes / pack / sr / lb / bantracker） | `/mc` |
| `MinecraftNews` | MC 新闻推送 | `/checkmcnews` |
| `MinecraftVersionChecker` | 最新 MC 版本 | `/mc ver` |
| `SkyblockResourcePackChecker` | Skyblock 资源包版本 | `/mc sr` |
| `DiZhenStatus` | 地震状态查询 | 定时任务推送 |
| `Calendar` | 每日日历 | `/calendar` / `/today` |
| `Feedback` | 反馈提交 | `/feedback` |
| `PingCommand` | 系统状态（CPU/内存/磁盘/运行时间） | `/ping` |
| `BoopCommand` | Boop!（万圣节变 Boo!，Kotlin 编写） | `/boop` |
| `BanTracker` | Hypixel 封禁追踪 | `/bantracker` |
| `HypixelTNTWizardsStats` | Hypixel TNT Wizards 数据 | `/wizard` |
| `HypixelZombies` | Hypixel Zombies 数据 | `/zombies` |
| `EarthOnline` | 地球 ONLINE | `/地球online` |
| `DebugCommand` | 控制台/QQ/Napcat Debug 模式开关 | 控制台 `/debug` |
| `LootsCommand` | 抽卡（每日免费 + 付费） | `/随机物品` |

### Napcat 功能（`function/napcat/`）

| 类 | 功能 | 说明 |
|----|------|------|
| `Repeater` | 自动复读 | 连续 3 条相同消息自动复读 |
| `AutoAcceptFriend` | 自动同意好友 | 自动同意好友请求并发送帮助 |
| `DenyFuckGuys` | 拒绝垃圾加群 | 拒绝带"请同意"等广告口吻的加群请求 |
| `UnknownInvitation` | 拒绝未知邀请 | 非管理员邀请自动退群 |
| `Notify` | 消息转发 | @ 和私聊消息转发到 debug 群 |
| `NotifyRecalled` | 撤回通知 | 撤回消息记录到 debug 群 |
| `GroupMessageCheck` | 敏感词检测 | 检测并自动撤回违规消息 |
| `GroupContentRecord` | 聊天记录 | 群消息存入 MySQL |
| `SearchRelevant` | 聊天记录搜索 | `/search "关键词" [-u QQ] [-m p/a]` |
| `CheckBilibili` | B 站视频解析 | 解析 BV 号并展示视频信息 |
| `AnnoyUser` | 表情轰炸 | `/emj` |
| `Broadcast` | 全服广播 | `/bc` |
| `RollbackMessages` | 批量撤回 | `/rollback` |
| `AutoPokeBack` | 自动回戳 | 被戳自动反戳 + 发图 |
| `Reboot` | 重启 Bot | `/reboot` |
| `GithubCommitNotify` | GitHub 推送 | Commit 推送自动生成图片通知（`function.github-webhook` 内嵌 HTTP Server） |
| `SetProjectInfo` | 项目信息 | 设置 Bot 状态信息 |
| `SizeNtUid` | 账号信息查询 | `/info @用户` |
| `AtriChat` | AI 聊天 | 关键词触发 AI 对话，使用 `AiProvider` |
| `like/CardLike` | QQ 点赞 | 关键词触发、手动点赞 |
| `like/AutoLikeCommand` | 点赞管理 | `/autolike` |
| `like/LikeUserListRecord` | 点赞名单持久化 | 内存 + 文件双层存储 |
| `personal/AnAnGirlEmoji` | 安安表情（Kotlin） | `/anan` |
| `personal/CucumberGirl` | 好女孩（Kotlin） | `/gt` |
| `personal/PinYin` | 拼音转换（Kotlin） | `/py` |
| `personal/MojiraStatus` | Mojira 漏洞追踪 | `/check-mojira` |
| `personal/EmailNotify` | 邮件通知 | IMAP 邮件监听通知 |
| `personal/AutoSendPtt` | 自动语音 | 定时发送语音消息 |
| `impl/MojiraIssueSummarizer` | Mojira 描述 AI 翻译 | 调用 AI 把英文 issue 翻成中文 |
| `classtable/` | 课表查询 | `ClassTableQueryUtil` + `ProcessClassTable` |

### 官方机器人功能（`function/official/`）

| 类 | 功能 | 说明 |
|----|------|------|
| `EventRecord` | 事件处理 | 新成员欢迎、群注册/注销、指令拦截、黑名单、C2C 主动消息授权回调 |
| `ChatContentRecord` | 消息记录 | 全平台消息存入 MySQL（`official_group_record` / `official_c2c_record`） |
| `SignCommand` | 每日打卡 | `/打卡` |
| `GoldsCommand` | 金粒余额 | `/golds` / `/金粒` |
| `WhoAmI` | 身份查询 | `/whoami` |
| `GroupCommand` | 群白名单/黑名单管理 | `/ogroup whitelist/blacklist/query` |
| `UserMgrCommand` | 权限管理 | `/perm setrole/add/remove` |
| `ConnectFourGame` | 四子棋 | `/connect4` / `/四子棋` |
| `MinesweeperGame` | 扫雷 | `/minesweeper` / `/扫雷` |
| `RockPaperScissorsGame` | 石头剪刀布 | `/rsp` / `/石头剪刀布` |
| `MiniGameCommand` | 小游戏菜单 | `/games` |
| `MusicCommand` | 音乐搜索 | `/music <关键词>`（内置 `A_Silent_Mirror` / `Biome_Fest`） |
| `PlayerProfile` | 玩家资料查询 | `/stats` |
| `RconHandler` | RCON 控制 | `/rc <服务器号> <命令>`（已注册 `atri` / `yl`） |
| `VerifyMinecraftCommand` | MC 账号验证 | `/verify <验证码>` |
| `PushTaskCommand` | 推送任务管理 | `/推送任务` |
| `FullMessageEnableCommand` | 全量消息授权 | `/全量消息` |
| `WebUICommand` | WebUI 管理 | `/webui`（开启/关闭、查看登录信息） |
| `AdminPauseCommand` | 紧急暂停 | `/pause`（切换 `ChatService.emergencyPaused`） |
| `BasicReply` | 群内关键词自动应答 | 监听多种官方消息事件 |
| `HypixelTNTWizardsStats` | TNT Wizards 数据 | `/wizard` |
| `HypixelZombies` | Zombies 数据 | `/zombies` |
| `EarthOnline` | 地球 ONLINE 图片 | `/地球online` |
| `loot/LootsCommand` | 抽卡 | `/随机物品` |
| `loot/LootService` | 抽卡核心业务 | 免费/付费抽、卡牌图渲染 |
| `loot/LootDao` / `LootAdminClient` | 抽卡数据 / 管理 API | 与 atrimeow-ugc 通信 |
| `minecraft/MinecraftBind` | MC 账号绑定 | 数据库绑定管理 |
| `minecraft/MinecraftRemote` | MC 远程控制 | 通过 Minecraft Socket 发送指令 |
| `minecraft/MinecraftCapes` | MC Capes 查询 | `/mc capes` |
| `minecraft/MinecraftWhitelist` | MC 玩家名白名单 | 提交白名单审核 |
| `minecraft/PackMcmetaGenerator` | 资源包生成 | `/mc pack` |
| `minecraft/PackVersion` | MC 版本数据缓存 | `Atri.onEnable()` 加载 |
| `tufe/TufeCheckHelp` | 电表查询帮助 | `/查询帮助` |
| `tufe/TufeElectricBind` | 电表绑定 | `/绑定 <宿舍号> <校区> <类型>` |
| `tufe/TufeElectricQuery` | 电表查询 | `/宿舍电表` / `/空调电表` |
| `tufe/TufeElectricService` | 电表核心逻辑 | 调用 di.tjufe.edu.cn 查询余额 |
| `imagesource/ImageSubmitCommand` | 图源投稿 | `/投稿` |
| `imagesource/ImageSourceStatsCommand` | 图源统计 | `/图源` |
| `imagesource/ImageReviewService` | 图源审核服务 | WebUI `ContentController` 调用 |
| `imagesource/ImageSourceClient` | 远端 UGC 通信 | upload / status / delete / deliver |
| `pushtask/` | 推送任务 | 内置 6 个 `PushTask` 实现：CalendarTask、HypixelNewsTask、HypixelAlphaTask、MinecraftNewsCheckTask、MemerAddWelcomeTask、SkyblockResourcePackTask |

### 定时任务（`function/task/`）

基于 `service/timer/` 的 `@Schedule` 注解：

| 类 | 说明 |
|----|------|
| `MessageStats` | 消息统计 `/chat` |
| `ManosabaDate` | Manosaba 日期（特定群推送） |
| `Calendar` | Napcat 端日历定时推送（向 `config.manosaba-group-id` 群） |
| `AutoSign` | 自动 SA 签到（Napcat） |
| `TufeClassAlert` | 课表提醒 `/tufe` |
| `CheckInExportTask` | 每日打卡数据导出 |
| `LootFreeDrawCleanupTask` | 每日免费抽卡记录清理 |
| `RefreshGroupProfilesTask` | 刷新官方群资料（每日 01:30） |

---

## Napcat 群组功能开关

Napcat 平台支持按群组独立开关功能，通过 `GroupConfigManager` 管理。每个群可独立控制以下功能（默认值由 `Atri.onEnable()` 注册）：

| 功能键 | 默认值 | 说明 |
|--------|--------|------|
| `auto_sign` | true | 自动签到 |
| `mc_news` | false | MC 新闻推送 |
| `hyp_news` | false | Hypixel 新闻推送 |
| `hyp_alpha_news` | false | Hypixel Alpha 公告推送 |
| `annoy_user` | true | 表情轰炸 |
| `new_year` | true | 新年倒计时 |
| `one_text` | true | 一言 |
| `repeat_msg` | false | 自动复读 |
| `send_poke` | true | 自动回戳 |
| `like_user` | false | QQ 点赞 |
| `mojang_status` | true | Mojang 状态 |
| `hypixel_status` | true | Hypixel 状态 |
| `github_info` | false | GitHub 推送 |
| `bv_check` | false | B 站视频解析 |
| `mojira_tracker` | false | Mojira 追踪 |
| `broadcast` | true | 全服广播 |
| `calendar` | true | 日历推送 |
| `get_hypixel_reward` | false | Hypixel 奖励 |
| `atri_chat` | false | AI 聊天 |
| `tufe_class_alert` | false | 课表提醒 |
| `private_func` | false | 私聊功能 |
| `illegal_words_check` | false | 敏感词检测 |

通过 WebUI `/webui/api/napcat/groups/{groupId}/features` 接口，或 Napcat 控制台指令 `/groupinfo`，可查看和修改群组配置。

---

## WebUI

项目包含一个 Vue 3 + Vite 前端（源码在 `webui/`，构建产物在 `src/main/resources/official-webui/`）。**所有平台共用一个 WebUI 入口**，启动后通过以下路径访问：

- **WebUI**：`http://host:port/webui`

`env: "dev"` 时 `Atri.onEnable()` 会自动调用 `WebUISessionManager.start()`；正式环境请用控制台 `webui` 指令临时开启。

### WebUI 页面（来自 `src/main/resources/official-webui/assets/`）

| 页面 | 入口组件 | 功能 |
|------|----------|------|
| Dashboard | `StatsView` | 数据概览、DAU、消息统计 |
| 群聊管理 | `ChatView` | 群消息查看、功能开关、发送消息、撤回 |
| C2C 私聊 | `ChatView` | 私聊消息查看、用户管理、发送消息 |
| 用户管理 | `UserGroupListView` | 用户列表、权限管理、角色设置 |
| 反馈管理 | `FeedbackView` | 反馈列表、回复反馈 |
| 图源管理 | `GalleryView` | 投稿图片审核、删除、统计 |
| 数据统计 | `StatsView` | 消息趋势、DAU 图表 |
| 错误报告 | `ErrorsView` | 错误日志查看、统计 |
| 发送日志 | `SendLogsView` | 官方 API 发送日志 |
| 原始事件 | `EventLogsView` | 原始 WebSocket 事件记录 |
| 入群审批 | `GroupStrategyView` | 策略管理、待审批列表、白名单 |
| 抽卡后台 | `LootView` | 抽卡物品 CRUD、用户背包、金粒调整 |
| MC 玩家名 | `MinecraftReviewView` | 玩家名白名单审核 |
| 自定义菜单 | `MenuPanelView` | 自定义指令菜单/面板编辑 |
| 功能开关 | `FunctionSettingsView` | 推送任务全局开关 |
| Napcat 管理 | `NapcatView` | Napcat 群组功能开关、消息记录、官方 API 调试 |
| API 调试 | `ApiDebugView` | 官方 API 调试工具 |
| 登录 | `LoginView` | Token 鉴权登录 |

### 鉴权

WebUI 使用会话 Cookie + Challenge/Nonce 机制（`WebUISessionManager`）。登录 Token 在 `config.yml` 的 `official-webui-token` 中配置。SSE 实时事件通过 `/webui/api/events` 推送（`SseBroadcaster`）。

---

## 公开 API

项目提供无需登录鉴权的公开查询接口，用于外部系统集成。详见 [`data/public_official_api.md`](data/public_official_api.md)。

基础路径：`/webui/api/public/official`

| 接口 | 说明 |
|------|------|
| `GET /group/messages/received` | 群聊接收消息计数 |
| `GET /group/messages/sent` | 群聊发送消息计数 |
| `GET /c2c/messages/received` | C2C 接收消息计数 |
| `GET /c2c/messages/sent` | C2C 发送消息计数 |
| `GET /dau` | DAU 和消息统计 |
| `GET /series` | 时间序列聚合（消息量、DAU 等） |
| `GET /sign?type=daily|overall` | 每日/累计签到数据 |
| `GET /users/{userOpenId}` | 用户信息查询 |
| `GET /groups/{groupOpenId}` | 群聊信息查询 |
| `POST /ntuid` | 单条 / 批量 NTUID 查询（`SizeNtUid` 接入，20 QPM/IP 频控） |

所有接口有 1 分钟内存缓存，支持 `start`/`end`/`startTime`/`from`/`endTime`/`to`/`all`/`groupOpenId`/`userOpenId`/`unionOpenId` 等参数。

公开查询接口频控：按 IP 60 次/分钟 + 全局 600 次/分钟，超出返回 HTTP 429；`/ntuid` 另有 20 QPM/IP 的独立频控。

---

## 构建与运行

### 构建

```bash
./gradlew shadowJar
```

构建产物在 `build/libs/AtriMeow-<version>.jar`（当前版本 `3.2.0-Release`）。

构建过程会自动生成 `git.properties`（位于 `build/generated/git/git.properties`），包含构建时间、版本号、Git commit hash 和分支名，最终会被打包进 `git.properties` 资源。

### 关键依赖

| 依赖 | 用途 |
|------|------|
| Javalin 6.6 | HTTP 服务器（WebUI、Napcat 回调） |
| Java-WebSocket 1.6 | QQ 官方机器人 / Discord WebSocket 网关 |
| HikariCP 7.1 + MySQL Connector 9.7 | 数据库连接池 |
| Jackson 2.22 | JSON 序列化 |
| SnakeYAML 2.6 | YAML 配置文件解析 |
| Sa-Token Sign 1.45 | API 签名鉴权 |
| Guava 33.6 | 缓存（消息序号、撤回记录等） |
| JSoup 1.23 + Readability4J 1.0 | 网页抓取与正文提取 |
| Lombok 1.18 | 代码生成（getter/setter/log 等） |
| Logback 1.6 | 日志框架 |
| Lunar 1.7 | 农历/日历计算 |
| Jpinyin 1.1 | 拼音转换 |
| Jakarta Mail 2.0 | IMAP 邮件监听 |
| Apache Commons Text 1.15 | 字符串转义工具 |
| JLine 4.3 | 控制台交互 |
| Kotlin Stdlib 2.1 | Kotlin 标准库（部分功能使用 Kotlin 编写） |

> 构建工具链：Gradle，Java Toolchain 25，开启 `--enable-preview`。

### Kotlin 文件

项目中有少量 Kotlin 文件，主要集中在以下位置：

| 文件 | 说明 |
|------|------|
| `function/general/BoopCommand.kt` | Boop 指令 |
| `function/napcat/personal/AnAnGirlEmoji.kt` | 安安表情 |
| `function/napcat/personal/CucumberGirl.kt` | 好女孩 |
| `function/napcat/personal/PinYin.kt` | 拼音转换 |

> 注：`event/EventType` 现已为 Java 枚举（`NAPCAT_GROUP_MESSAGE / OFFICIAL_GROUP_MESSAGE / OFFICIAL_GROUP_AT_MESSAGE / OFFICIAL_C2C_MESSAGE / OFFICIAL_GUILD_CHANNEL / OFFICIAL_GUILD_DM / DISCORD_SLASH_COMMAND`）。

---

## CI/CD

项目通过 GitHub Actions 实现自动化构建与发布：

- **触发条件**：推送到 `AtriMeow` 分支
- **构建环境**：Ubuntu + Java 25 (Temurin) + Gradle
- **构建命令**：`./gradlew build -x test --no-daemon`
- **发布**：自动创建 GitHub Release，附带构建产物 JAR

工作流文件位于 `.github/workflows/release.yml`。

---

## 项目结构

```
src/main/java/top/yzljc/atribot/
├── Atri.java                     # 主入口，初始化和生命周期管理
├── configuration/                # 配置类
│   ├── Config.java               #   主配置加载
│   ├── Properties.java           #   常量/资源路径
│   ├── ResourcesProperties.java  #   资源/外部 API URL 常量
│   ├── LoadIllegalWords.java     #   敏感词加载
│   └── ImageDelivery.java        #   投递链接解析
├── chat/
│   ├── napcat/                   # Napcat 消息发送门面
│   │   ├── GroupMessage.java     #   群聊发送
│   │   ├── PrivateMessage.java   #   私聊发送
│   │   ├── FriendList.java       #   好友列表
│   │   ├── GroupInformation.java #   群信息查询
│   │   ├── UserInformation.java  #   用户信息查询
│   │   ├── SendPoke.java         #   戳一戳
│   │   └── impl/
│   │       ├── MessageUtils.java #   底层实现（构建段、发 HTTP）
│   │       └── MessageSegment.java # 消息段 record
│   ├── official/                 # 官方机器人消息发送门面
│   │   ├── GroupChat.java        #   群聊同步门面
│   │   ├── AsyncGroupChat.java   #   群聊异步门面
│   │   ├── C2CChat.java          #   C2C 同步门面
│   │   ├── AsyncC2CChat.java     #   C2C 异步门面
│   │   ├── GuildChannelChat.java #   频道子频道同步门面
│   │   ├── AsyncGuildChannelChat.java
│   │   ├── GuildDirectChat.java  #   频道私信同步门面
│   │   ├── AsyncGuildDirectChat.java
│   │   ├── PrivateStreamMessage.java  # C2C 流式消息辅助
│   │   ├── ChatService.java      #   底层实现（HTTP API 调用）
│   │   ├── ActiveMessageRateLimiter.java
│   │   ├── OfficialMediaUploader.java  # 媒体上传
│   │   ├── QQMessageSendException.java
│   │   ├── MessageBody.java      #   消息体 DTO
│   │   ├── MessageBodyFactory.java
│   │   ├── Markdown.java         #   Markdown 辅助构造
│   │   ├── TC.java               #   快捷工具（md / keyboard / promptKeyboard）
│   │   ├── button/               #   按钮模型
│   │   │   ├── Button.java
│   │   │   ├── ButtonSize.java
│   │   │   ├── ButtonStyle.java
│   │   │   ├── ButtonType.java
│   │   │   └── PermissionType.java
│   │   ├── media/                #   消息/颜色类型
│   │   │   ├── GroupMessageType.java
│   │   │   └── HexColor.java
│   │   ├── management/           #   入群审批/禁言
│   │   │   ├── JoinRequestApproval.java
│   │   │   ├── JoinApprovalStrategy.java
│   │   │   └── Mute.java
│   │   ├── menu/                 #   自定义菜单
│   │   │   └── Menu.java
│   │   └── panel/                #   指令面板
│   │       └── Panel.java
│   ├── discord/                  # Discord 消息构造
│   │   ├── DiscordComponents.java
│   │   ├── DiscordEmbed.java
│   │   └── DiscordMessagePayload.java
│   ├── ImageComponent.java       # 图片封装（URL / BASE64 / FILE + text）
│   └── ImageType.java            # 图片类型枚举
├── event/                        # 事件系统
│   ├── EventManager.java         #   事件注册/派发
│   ├── EventHandler.java         #   @EventHandler 注解
│   ├── EventPriority.java        #   优先级枚举
│   ├── EventType.java            #   事件类型枚举
│   ├── Listener.java             #   监听器接口
│   ├── Cancellable.java          #   可取消接口
│   ├── Event.java                #   事件基类
│   ├── events/                   #   所有事件类
│   │   ├── Napcat*Event.java     #     Napcat 事件
│   │   ├── Official*Event.java   #     官方机器人事件
│   │   ├── Discord*Event.java    #     Discord 事件
│   │   ├── EmailMessageEvent.java
│   │   └── UserRunCommandEvent.java
│   └── impl/                     #   事件相关枚举
│       ├── AnswerCode.java
│       ├── GroupMemberChangeType.java
│       ├── RecallType.java
│       ├── RequestSource.java
│       ├── VerifyMethod.java
│       ├── ErrorCode.java
│       ├── InteractionType.java
│       └── UnknownButtonInteractionScene.java
├── command/                      # 指令系统
│   ├── CommandManager.java       #   指令注册/分发
│   ├── CommandSender.java        #   通用 sender
│   ├── NapcatCommandSender.java
│   ├── QQCommandSender.java
│   ├── QQGuildCommandSender.java
│   ├── DiscordCommandSender.java
│   ├── ConsoleCommandSender.java
│   ├── Command.java              #   指令抽象类
│   ├── CommandFeature.java       #   可绑定执行器的指令
│   ├── CommandDefinition.java    #   YAML 解析后的定义
│   ├── CommandOptionDefinition.java  #   Discord Slash 参数声明（YAML → Discord JSON）
│   ├── CommandExecutor.java      #   普通指令执行器
│   ├── SlashCommandExecutor.java #   Discord Slash 指令执行器
│   ├── SlashCommandArguments.java
│   ├── CommandMap.java           #   底层存储
│   └── impl/                     #   各平台 sender 实现
│       ├── NapcatSenderImpl.java
│       ├── QQSenderImpl.java
│       ├── QQGuildSenderImpl.java
│       ├── DiscordSenderImpl.java
│       └── ConsoleSenderImpl.java
├── platform/                     # 平台抽象层
│   ├── Platform.java             #   平台枚举（8 个值）
│   ├── PlatformRole.java         #   平台角色枚举（OWNER/ADMIN/MEMBER）
│   ├── User.java                 #   用户模型
│   ├── Message.java              #   消息模型
│   ├── Identifier.java           #   标识符常量（错误码、禁止提示等）
│   ├── MessageType.java
│   ├── Recallable.java
│   ├── UnsupportedPlatform.java  #   不支持平台异常
│   ├── napcat/                   #   Napcat 平台
│   │   ├── RequestReceiver.java  #     HTTP 请求接收
│   │   ├── PostRequest.java
│   │   ├── RequestType.java
│   │   ├── NapcatMessage.java
│   │   └── groupfunction/
│   │       ├── GroupConfigManager.java
│   │       ├── GroupConfigInfo.java
│   │       └── GroupModeManager.java
│   ├── qq/                       #   QQ 官方平台
│   │   ├── BotEvents.java        #     WebSocket 事件解析
│   │   ├── FileType.java         #     媒体类型
│   │   ├── GroupProfile.java
│   │   ├── OfficialManager.java  #     WebSocket 管理
│   │   ├── QQBot.java            #     Bot 信息
│   │   ├── QQMessage.java        #     消息模型
│   │   ├── TokenManager.java     #     Token 管理
│   │   └── WebSocketClient.java  #     WS 客户端
│   └── discord/                  #   Discord 平台
│       ├── DiscordManager.java
│       ├── DiscordWebSocketClient.java
│       ├── DiscordMessage.java
│       └── DiscordUser.java
├── function/                     # 功能模块
│   ├── general/                  #   通用功能（含 .kt）
│   ├── napcat/                   #   Napcat 专有功能
│   │   ├── like/
│   │   ├── personal/             #     含 .kt
│   │   ├── impl/                 #     MojiraIssueSummarizer
│   │   └── classtable/           #     课表查询
│   ├── official/                 #   官方机器人专有功能
│   │   ├── minecraft/            #     Minecraft 相关
│   │   ├── tufe/                 #     宿舍电表
│   │   ├── imagesource/          #     图源投稿
│   │   ├── loot/                 #     抽卡
│   │   └── pushtask/             #     推送任务
│   ├── impl/                     #   通用实现
│   │   ├── ArticleScraper.java
│   │   ├── AtriNewsSummarizer.java
│   │   ├── FetchHitokoto.java
│   │   ├── ImageDTO.java
│   │   ├── ImageReviewStatus.java
│   │   └── PreImageGenerate.java
│   ├── console/                  #   占位（控制台命令目前直接在 general/）
│   └── task/                     #   基于 @Schedule 的定时任务
├── service/                      # 服务层
│   ├── Scheduler.java            #   调度器
│   ├── ai/                       #   AI 服务（多提供商）
│   │   ├── AiService.java
│   │   ├── AiProvider.java
│   │   └── AiProperties.java
│   ├── email/                    #   邮件服务（IMAP）
│   │   └── IMAP.java
│   ├── request/                  #   HTTP 请求服务
│   │   ├── HttpService.java
│   │   └── SaSignHeader.java
│   ├── runtime/                  #   线程/控制台
│   │   ├── ThreadManager.java
│   │   ├── ConsoleManager.java
│   │   └── JLineConsoleAppender.java
│   ├── taskscheduler/            #   声明式任务调度（TaskScheduler）
│   │   ├── TaskScheduler.java
│   │   ├── TaskSchedulerRegistry.java
│   │   ├── TaskSchedule.java
│   │   ├── DefaultTaskSchedule.java
│   │   ├── ScheduledTask.java
│   │   ├── ScheduledTaskHandle.java
│   │   ├── ScheduleMode.java
│   │   └── TaskScheduleTimes.java
│   └── timer/                    #   @Schedule 注解扫描器
│       ├── RunScheduleTask.java
│       ├── Schedule.java         #     @Schedule 注解
│       ├── Schedules.java
│       └── ScheduleType.java
├── database/                     # 数据库层
│   ├── DatabaseManager.java      #   HikariCP 连接池
│   ├── *DTO.java                 #   数据传输对象
│   └── repo/                     #   仓库层
│       ├── GroupRepository.java
│       ├── C2CRepository.java
│       ├── SignRepository.java
│       ├── FeedbackRepository.java
│       ├── ErrorReportRepository.java
│       ├── ImageSourceRepository.java
│       ├── PendingNoticeRepository.java
│       ├── EventLogRepository.java
│       ├── OfficialSendLogRepository.java
│       ├── CoinGainLogRepository.java
│       ├── LootRepository.java
│       ├── TufeElecRepository.java
│       └── UnifiedAccountRepository.java
├── auth/                         # 权限/身份
│   ├── AccountStatus.java
│   ├── UnifiedAccount.java
│   ├── UnifiedAuthentication.java
│   ├── UACommand.java            #   /ua 统一身份验证指令
│   └── official/
│       ├── FullMessageAuth.java  #   全量消息授权 Markdown
│       ├── OfficialGroups.java   #   群白/黑名单
│       ├── OfficialUsers.java    #   C2C 用户权限
│       └── UnifiedRole.java      #   OWNER/ADMIN/USER
├── webui/                        # WebUI 后端
│   ├── Result.java               #   统一响应格式
│   ├── SseBroadcaster.java       #   SSE 推送
│   ├── WebUIRouter.java          #   路由注册
│   ├── WebUISessionManager.java  #   会话管理
│   ├── WebUiSupport.java         #   公共工具
│   ├── controller/               #   REST 控制器
│   │   ├── AdminController.java
│   │   ├── AuthController.java
│   │   ├── C2CController.java
│   │   ├── ContentController.java
│   │   ├── GroupController.java
│   │   ├── JoinApprovalController.java
│   │   ├── MenuController.java
│   │   ├── MinecraftWhitelistController.java
│   │   ├── NapcatController.java
│   │   ├── PanelController.java
│   │   └── PublicQueryController.java
│   └── repo/                     #   WebUI 仓库层
│       ├── ChatPinnedRepo.java
│       ├── ChatStatsSnapshotRepo.java
│       ├── JoinApprovalSnapshotRepo.java
│       ├── JoinApprovalWhitelistRepo.java
│       ├── OrphanedGroupRecordCleanup.java
│       └── PublicOfficialQueryRepo.java
├── utils/                        # 工具类
│   ├── YamlConfiguration.java    #   YAML 配置工具
│   ├── FormatTools.java          #   格式化工具
│   ├── GetProjectInfo.java       #   项目信息
│   ├── ErrorReport.java          #   错误上报
│   ├── RemoteServerErrorException.java
│   ├── ServerNoResponseException.java
│   ├── debug/                    #   调试工具
│   │   └── NapcatPacket.java
│   ├── notify/                   #   通知服务
│   │   ├── NotificationService.java
│   │   └── PendingNoticeDispatcher.java
│   ├── socket/                   #   Minecraft Socket
│   │   ├── MinecraftSocket.java
│   │   └── BindResponse.java
│   ├── statistic/                #   运行时数据统计
│   │   ├── BotRuntimeData.java
│   │   └── RuntimeData.java
│   ├── tools/                    #   工具类
│   │   ├── Alert.java
│   │   ├── RM.java
│   │   ├── RandomGolds.java
│   │   └── FetchMinecraftProfile.java
│   └── update/                   #   更新推送
│       ├── UpdatePushCommand.java
│       └── UpdateNoticeRecord.java

src/main/java/top/yzljc/sakuraba_ema/   # QQ 频道 CLI 客户端
├── ChannelCliClient.java           # CLI 调用主入口
├── guild/
│   ├── ChannelInformation.java     # 频道信息查询
│   ├── ChannelPosts.java           # 帖子（发帖 / 回帖 / 转发）
│   └── impl/
│       ├── ChannelCliException.java
│       ├── ChannelCliOptions.java
│       ├── ChannelCliResult.java
│       └── ChannelFeedClient.java  # 论坛 Feed 抓取
├── manager/
│   ├── ChannelManageClient.java    # 频道管理
│   └── ChannelSystemClient.java    # 频道系统接口
└── utils/
    └── ForumCode.java              # 论坛渲染（BBCode/Markdown）

src/main/resources/
├── config.yml                     # 默认配置（首次运行复制到工作目录）
├── atribot.yml                    # 指令声明（YAML）
├── official-webui/                # WebUI 前端构建产物
└── ...
```

---

## License

MIT License © 2026 YZ_Ljc_
