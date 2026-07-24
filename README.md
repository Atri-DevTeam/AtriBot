# AtriMeow - 亚托利喵QQ机器人

基于 Java/Kotlin 的多平台机器人，同时适配 **Napcat（OneBot 协议）**、**QQ 官方机器人 API** 与 **Discord**，注意到，本项目的一些写法与`Bukkit`高度相似

免责声明：`撰写者为什么都不会的新手，且部分复杂逻辑使用了AIGC，说人话就是这是屎山，仅部分内容能参考一下（虽然但是，我的屎山能跑！！！）`

为什么有些地方使用了Kotlin呢？因为作者想学着写，这个项目本质上是一个练手作品，所以有些稀烂的地方恳请理解

可以支持一下我们的机器人喵 `亚托利喵` `UIN: 3889798968`

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

项目同时运行三套适配层，共享同一套事件系统和指令系统：

| 层级 | Napcat（OneBot） | QQ 官方机器人 | Discord |
|------|------------------|---------------|---------|
| 入口 | HTTP POST `127.0.0.1:port/` | WebSocket 网关连接 | WebSocket 网关连接 |
| 消息发送 | `chat/napcat/` | `chat/official/` | `platform/discord/` |
| 消息类型 | 文本、图片(URL/BASE64/FILE)、@、回复、合并转发 | 文本、Markdown、图片(MEDIA)、文件、键盘按钮 | 文本、Embed、Slash Commands |
| 事件类 | `Napcat*Event` | `Official*Event` | `Discord*Event` |
| 功能类 | `function/napcat/` | `function/official/` | `function/discord/` |
| 平台枚举 | `NAPCAT_GROUP` / `NAPCAT_PRIVATE` | `OFFICIAL_GROUP` / `OFFICIAL_C2C` | `DISCORD_GUILD` / `DISCORD_DM` |

`Napcat` 的网络接口是 `HTTP` 模式，`QQ 官方机器人` 和 `Discord` 均通过 WebSocket 网关连接

三个平台通过 `CommandManager` 统一处理指令，通过 `EventManager` 统一派发事件

---

## 快速开始

### 环境要求

- JDK 25+（构建工具链指定 Java 25，开启 `--enable-preview`）
- MySQL 数据库
- Napcat 服务端（使用 OneBot 时）或 QQ 开放平台 AppID/Secret（使用官方机器人时）或 Discord Bot Token（使用 Discord 时）

### 运行

1. 将 `src/main/resources/config.yml` 复制到 jar 同级目录，按需填写（见下方[配置文件](#配置文件)）
2. `java -jar AtriMeow.jar`
3. 控制台输入 `stop` 可安全关闭。

---

## 配置文件

配置文件为 jar 同级目录下的 `config.yml`，首次运行会自动生成带默认值的模板。以下为各区块说明：

### 全局设置

```yaml
command-prefix: "/"           # 指令前缀，所有平台通用
debug-command-suffix: "--debug"  # Debug 命令后缀
debug-mode: false             # Debug 模式，开启后部分功能行为变化
ttf-file-name: "default.ttf"  # 根目录字体文件名（默认鸿蒙开源字体）
listen-port: 1234             # HTTP 服务器监听端口（WebUI、Napcat 回调）
api-url: null                 # 外部 API 地址

mysql:                        # MySQL 数据库连接（必填）
  host: "localhost"
  port: 3306
  database: "database"
  username: "root"
  password: "null"
```

### AI 服务（多提供商）

AI 服务支持多提供商配置，按名称区分。每个子节点为一个独立提供商，额外字段（如 `temperature`、`top_p`、`max_tokens`）会原样合并到请求 body 中：

```yaml
ai:
  default:
    api-key: ""
    base-url: ""            # 需包含 /v1/chat/completions
    model: "qwen3.5-flash"
    timeout: 30000000
  dickseek:
    api-key: ""
    base-url: ""
    model: "qwen3.5-flash"
    timeout: 30000000
    # temperature: 1
    # top_p: 0.95
    # max_tokens: 16384
```

代码中通过 `AiProvider` 枚举（`DEFAULT`、`OTHER`、`OPENCODE` 等）选择提供商。

### Napcat 适配器

```yaml
napcat:
  enabled: false                    # 是否启用 Napcat 适配器
  server-url: "http://0.0.0.0:12345"  # Napcat HTTP 服务地址
  debug-group-uin: 123456789        # Debug 群号（用于日志/通知转发）
  bot-uin: 123456789                # Bot 自身 QQ 号
  admin-uins:                       # 管理员 QQ 号列表
    - "123456789"
  message-spy-groups:               # 消息监听群列表（转发 @ 和私聊到 debug 群）
    - "123456"
  recall-ignore-user:               # 撤回监听屏蔽用户
    - "123456"
```

### 官方机器人

```yaml
qq:
  enabled: false                                          # 是否启用官方机器人
  app-id: ""                                              # QQ 开放平台 AppID
  client-secret: ""                                       # QQ 开放平台密钥
  api-base-url: "https://sandbox.api.sgroup.qq.com"       # 沙箱/正式环境
  official-webui-token: "your-random-token"               # WebUI 登录 Token（自行生成）
  official-openId: ""                                     # Bot 自身 OpenId
  official-username: ""                                   # Bot 名称
  debug-group-openId: ""                                  # Debug 群 OpenId
  super_admin_id: "null"                                  # 超级管理员用户 OpenId
```

### Discord 机器人

```yaml
discord:
  enabled: false
  bot-token: ""
  api-base-url: "https://discord.com/api/v10"
  intents: 37377   # 默认包含 GUILDS / GUILD_MESSAGES / DIRECT_MESSAGES / MESSAGE_CONTENT
```

### 功能配置

```yaml
atribot-key-secret: "null"     # AtriBot Key Secret

function:
  hypixel-reward-ws: "ws://localhost:1111"  # Hypixel 奖励 WebSocket
  sa-sign-key: "null"                       # SA 签到密钥
  github-webhook:           # GitHub Webhook（Commit 推送）
    port: 54321
    secret: "your-secret"
  bilibili-cookie: ""       # B 站视频解析用的 Cookie
  wakeup-image-link: ""     # 叫醒表情包链接
  keywords-hitokoto:        # 触发"一言"的关键词
    - "一言"
    - "hitokoto"
    - "yiyan"
  keywords-like-user:       # 触发"点赞"的关键词
    - "点赞"
    - "likeme"
    - "zanwo"
    - "赞我"
```

### Minecraft 验证服务器

```yaml
verify:
  port: 8080
  host: "127.0.0.1"
  key: "public-key"    # RSA 公钥
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

启用后可监听邮箱新邮件并触发通知推送。

### 图源投稿

```yaml
image-source:
  enabled: false
  upload-url: "https://qq-ugc.yzljc.top/imagesource/upload"
  delete-url: "https://qq-ugc.yzljc.top/imagesource/delete"
  view-base-url: "https://qq-ugc.yzljc.top/imagesource/get"
  token: "null"
  pending-limit: 3    # 单用户同时未审核投稿上限
```

### 其他配置

```yaml
# 特殊群推送
manosaba-group-id: 123456

# 加群验证
group-join-verify-group-id: 123456789
group-join-verify-message: "欢迎加入，请在30秒内发送验证消息"
group-join-verify-timeout-seconds: 60
group-join-verify-answer: "验证"

# 官方机器人活跃消息群列表
official-active-message-groups:
  - "123456"

# 云大陆（Yunland）连接
yunland:
  host: "null"
  port: 12345
  connect-key: "null"
```

---

## 消息发送

### Napcat（OneBot）

Napcat 消息发送统一使用 `GroupMessage`（群聊）和 `PrivateMessage`（私聊）两个门面类，底层由 `MessageUtils` 实现。所有发送方法返回 `messageId`（成功）或 `null`（失败）

#### 群聊消息

```java
// 发送纯文本
GroupMessage.chatMessage(groupId, "你好世界");

// 发送文本并 @ 用户
GroupMessage.chatMessage(userId, groupId, "你好", true);

// 发送图片（三种来源）
GroupMessage.chatMessage(groupId, "https://example.com/img.png", MessageUtils.ImageType.URL);
GroupMessage.chatMessage(groupId, "base64...", MessageUtils.ImageType.BASE64);
GroupMessage.chatMessage(groupId, "/path/to/img.png", MessageUtils.ImageType.FILE);

// 发送文本+图片
GroupMessage.chatMessage(groupId, "看这张图", "https://example.com/img.png", MessageUtils.ImageType.URL);

// 回复消息
GroupMessage.replyMessage(groupId, messageId, "回复内容");
GroupMessage.replyMessage(groupId, messageId, "https://example.com/img.png", MessageUtils.ImageType.URL);

// @ 某人
GroupMessage.atUser(userId, groupId, "提醒你");

// 撤回消息
GroupMessage.recallMessage(messageId);
```

#### 私聊消息

```java
// 发送文本
PrivateMessage.chatMessage(userId, "你好");

// 发送图片
PrivateMessage.chatMessage(userId, "https://example.com/img.png", MessageUtils.ImageType.URL);

// 回复消息
PrivateMessage.replyMessage(userId, messageId, "回复内容");
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

Napcat 使用 `MessageSegment(String type, Map<String, Object> data)` 表示消息段：

| type | data 参数 | 说明 |
|------|-----------|------|
| `text` | `{"text": "..."}` | 文本 |
| `image` | `{"url": "..."}` 或 `{"file": "base64://..."}` 或 `{"file": "file://..."}` | 图片 |
| `at` | `{"qq": "123456"}` | @某人 |
| `reply` | `{"id": "msgId"}` | 引用回复 |
| `node` | `{"uin": "...", "name": "...", "content": [...]}` | 转发节点 |

---

### 官方机器人

官方机器人使用 `GroupChat`（群聊）和 `C2CChat`（私聊）两个静态门面类，底层由 `ChatService` 实现。支持 **文本、Markdown、图片、文件、键盘按钮** 等消息类型

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

// 发送 Markdown 带键盘按钮（最大支持5x5）
Object keyboard = TC.keyboard(List.of(
    List.of(
        new Button("btn1", "打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
        new Button("btn2", "帮助", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND)
    )
));
GroupChat.sendMessage(groupOpenId, TC.md("请选择操作："), keyboard);

// 发送图片
GroupChat.sendMessage(groupOpenId, ImageType.URL, "https://example.com/img.png");
GroupChat.sendMessage(groupOpenId, ImageType.BASE64, "base64...");

// 回复文本消息
GroupChat.replyMessage(groupOpenId, msgId, "回复文本");

// 回复 Markdown（会自动 @ 原消息发送者）
GroupChat.replyMessage(groupOpenId, userOpenId, msgId, TC.md("回复的 **Markdown**"));

// 回复 Markdown 带键盘
GroupChat.replyMessage(groupOpenId, userOpenId, msgId, TC.md("选一个："), keyboard);

// 回复图片
GroupChat.replyMessage(groupOpenId, msgId, ImageType.URL, "https://example.com/img.png");

// 回复文件
GroupChat.replyMessage(groupOpenId, msgId, fileType, "fileUrl");

// 新成员欢迎消息
GroupChat.welcomeMessage(groupOpenId, memberOpenId, eventId,
    TC.md(Markdown.at(memberOpenId) + " 欢迎新人喵~\n" + Markdown.img(welcomeImg, 1254, 1254)),
    welcomeButtons
);

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
C2CChat.sendMessage(openId, ImageType.URL, "https://example.com/img.png");

// 回复消息（文本/Markdown/图片）
C2CChat.replyMessage(openId, msgId, "回复文本");
C2CChat.replyMessage(openId, msgId, TC.md("**回复** Markdown"));
C2CChat.replyMessage(openId, msgId, ImageType.URL, "https://example.com/img.png");

// 撤回
C2CChat.recallMessage(openId, messageId);
```

#### 官方机器人消息类型总结

| 类型 | 枚举/类 | 说明                                        |
|------|---------|-------------------------------------------|
| 文本 | `GroupMessageType.TEXT(0)` | 普通文本消息                                    |
| Markdown | `GroupMessageType.MARKDOWN(2)` | 支持 QQ 官方 Markdown 语法（加粗、标题、链接、图片、@、命令按钮等） |
| 媒体(图片) | `GroupMessageType.MEDIA(7)` | 需先上传文件，再发送 media 消息                       |
| 文件 | `GroupMessageType.MEDIA(7)` | 与图片同一通道，file_type 不同                      |
| ARK | `GroupMessageType.ARK(3)` | ARK 模板消息（几乎不可用）                           |
| Embed | `GroupMessageType.EMBED(4)` | Embed 消息                                  |

#### Markdown 辅助方法（`Markdown` 类）

```java
Markdown.img("https://...", 800, 600)                    // 嵌入图片
Markdown.img("替代文本", "https://...", 800, 600)         // 带替代文本的图片
Markdown.at(userOpenId)                                   // @用户
Markdown.atAll()                                          // @全体成员（仅限频道可用）
Markdown.enterCommand("/打卡")                            // 可点击的命令
Markdown.enterCommand("/打卡", "点我打卡")                 // 自定义显示文字
Markdown.link("https://...", "链接文字")                  // 超链接
```

#### 按钮（`Button` 类）

```java
new Button(
    "buttonId",           // 按钮唯一标识
    "显示文字",            // displayText
    "/command data",      // data（回传数据）
    true,                 // enter（是否自动发送）
    ButtonStyle.BLUE,     // 样式：GRAY / BLUE / RED / BLUE_WITH_BACKGROUND / ICON_BUTTON
    ButtonType.COMMAND    // 类型：COMMAND / CALLBACK / LINK
)
```

按钮回调会触发 `OfficialInteractionEvent`，可通过 `event.getButtonId()` 和 `event.getButtonValue()` 获取回调数据

---

## 事件系统

事件系统是统一的消息/通知处理框架，三个平台的底层事件都会经过它

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

| 事件类 | 触发场景                     | 可取消 |
|--------|--------------------------|--------|
| `OfficialGroupMessageCreateEvent` | 收到群消息（含 `isAtBot`）       | 否 |
| `OfficialGroupAtMessageCreateEvent` | 收到群 @ 消息（开启全量消息后此事件不再接收） | 否 |
| `OfficialC2CMessageCreateEvent` | 收到私聊消息                   | 否 |
| `OfficialInteractionEvent` | 按钮点击等交互                  | 否 |
| `OfficialGroupJoinEvent` | Bot 被加入群聊                | 否 |
| `OfficialGroupDelEvent` | Bot 被移出群聊                | 否 |
| `OfficialFriendAddEvent` | 被添加好友                    | 否 |
| `OfficialFriendDelEvent` | 被删除好友                    | 否 |
| `OfficialGroupMemberAddEvent` | 群新成员加入                   | 否 |
| `OfficialGroupMemberRemoveEvent` | 群成员退出                    | 否 |
| `OfficialActiveMessageFailEvent` | 主动消息推送失败                 | 否 |

#### Discord 事件

| 事件类 | 触发场景 |
|--------|---------|
| `DiscordMessageEvent` | 收到 Discord 消息 |
| `DiscordSlashCommandEvent` | 收到 Slash Command |

#### 通用事件

| 事件类 | 触发场景 | 可取消 |
|--------|---------|--------|
| `UserRunCommandEvent` | 用户执行指令前 | **是** |

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
event.sendMessage("https://...", ImageType.URL); // 图片
event.isAtBot();                                // 是否 @ 了机器人
```

**OfficialInteractionEvent**（按钮回调）：
```java
event.getButtonId();         // 获取按钮 ID
event.getButtonValue();      // 获取按钮回传数据
event.sendMessage("文本");    // 根据 chatType 自动选择群聊或私聊回复
event.answer(AnswerCode.SUCCESS);  // 回复交互确认（必须手动执行，否则客户端将判定超时）
```

---

## 指令系统

指令通过 `CommandManager` 统一管理，同时监听 Napcat、官方机器人和 Discord 的消息事件

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
        
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true; // 对于官方群聊、私聊、第三方机器人的指令时，必须对不同平台的行为进行约束，避免抛出`UnsupportedOperationException`
        
        sender.sendMessage("你执行了 /" + label + "，参数：" + String.join(" ", args));
        return true; // return false 将发送默认的指令使用帮助
    }
}
```

#### 3. 在 `Atri.java` 的 `onEnable()` 中的指令注册方式

```java
CommandManager.getCommand("mycommand").setExecutor(new MyCommandExecutor());
```

### Discord Slash Command

Discord 平台额外支持 Slash Command，实现 `SlashCommandExecutor` 接口：

```java
public class MySlashCommand implements SlashCommandExecutor {
    @Override
    public boolean onSlashCommand(DiscordSlashCommandSender sender, Command command, String label, SlashCommandArguments args) {
        sender.sendMessage("Pong!");
        return true;
    }
}
```

### CommandSender 常用方法

```java
// sender 自动适配平台（Napcat 群/私聊、官方群/私聊、Discord）
sender.sendMessage("文本回复");              // 自动选择正确的发送方式
sender.hasPermission();                      // 检查是否管理员
sender.getPlatform();                        // 获取平台枚举
sender.getUserId();                          // 获取用户 ID
sender.getGroupId();                         // 获取群 ID（私聊时为 null）
sender.getMentions();                        // 获取消息中 @ 的用户列表
```

---

## 延迟执行与异步任务

项目提供三套工具来执行延迟和异步任务：`Scheduler`、`ThreadManager` 和 `TaskScheduler`。

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

`ThreadManager` 是全局静态工具类，内部使用**虚拟线程**（Virtual Threads）执行任务，通过信号量控制最大并发数。适合需要 `CompletableFuture` 链式调用或不需要 `Atri` 实例持久的场景

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

### TaskScheduler

`TaskScheduler` 是基于注解的声明式调度系统，支持 `daily`、`hourly`、`half_hour`、`a_quarter` 四种调度模式。定时任务类位于 `function/task/` 包下，通过 `@Schedule` 注解声明调度规则，启动时由 `TaskSchedulerRegistry` 自动注册。

### 使用建议

- **简单延迟/定时**：用 `Scheduler`，通过 `Atri.getInstance().getScheduler()` 获取
- **需要链式异步处理**：用 `ThreadManager.supplyAsync()` 获取 `CompletableFuture`
- **一次性 fire-and-forget**：用 `ThreadManager.execute()`
- **声明式定时任务**：用 `TaskScheduler` + `@Schedule` 注解
- 所有任务执行都有异常捕获和日志记录，不会因未捕获异常导致线程崩溃

---

## AI 服务

`AiService` 提供多提供商 AI 对话能力，内部使用 OpenAI 兼容的 Chat Completions API。

```java
AiService aiService = Atri.getInstance().getAiService();

// 使用默认提供商
String reply = aiService.ask("你好");

// 指定提供商
String reply = aiService.ask(AiProvider.DEFAULT, "你好");

// 自定义 System Prompt
String reply = aiService.askWithSystemPrompt("你好", "你是一个助手");
```

默认 System Prompt 将 AI 人设为《ATRI -My Dear Moments-》中的机器人少女亚托莉，可通过 `askWithSystemPrompt` 自定义。

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
| `HypixelReward` | 领取 Hypixel 奖励 | `/cl` |
| `HypixelStatus` | Hypixel 服务器状态 | `/hypstatus` |
| `MinecraftCommand` | MC 子命令（骰子、版本、Capes、资源包） | `/mc` |
| `MinecraftNews` | MC 新闻推送 | `/checkmcnews` |
| `DiZhenStatus` | 地震状态查询 | 定时任务推送 |
| `Calendar` | 每日日历 | `/calendar` / `/today` |
| `Feedback` | 反馈提交 | `/feedback` |
| `HypixelAnnouncements` | Hypixel 公告推送 | `/check-hyp` |
| `PingCommand` | 系统状态（CPU/内存/磁盘/运行时间） | `/ping` |
| `BoopCommand` | Boop!（万圣节变 Boo!） | `/boop` |
| `BanTracker` | Hypixel 封禁追踪 | `/bantracker` / `/mc bt` |

### Napcat 功能（`function/napcat/`）

| 类 | 功能 | 说明 |
|----|------|------|
| `Repeater` | 自动复读 | 连续 3 条相同消息自动复读 |
| `AutoAcceptFriend` | 自动同意好友 | 自动同意好友请求并发送帮助 |
| `DenyFuckGuys` | 拒绝垃圾加群 | 拒绝带广告的加群请求 |
| `UnknownInvitation` | 拒绝未知邀请 | 非管理员邀请自动退群 |
| `Notify` | 消息转发 | @ 和私聊消息转发到 debug 群 |
| `NotifyRecalled` | 撤回通知 | 撤回消息记录到 debug 群 |
| `GroupMessageCheck` | 敏感词检测 | 检测并自动撤回违规消息 |
| `GroupContentRecord` | 聊天记录 | 群消息存入 MySQL |
| `SearchRelevant` | 聊天记录搜索 | `/search` |
| `CheckBilibili` | B 站视频解析 | 解析 BV 号并展示视频信息 |
| `AnnoyUser` | 表情轰炸 | `/emj` |
| `Broadcast` | 全服广播 | `/bc` |
| `RollbackMessages` | 批量撤回 | `/rollback` |
| `AutoPokeBack` | 自动回戳 | 被戳自动反戳 + 发图 |
| `Reboot` | 重启 Bot | `/reboot` |
| `GithubCommitNotify` | GitHub 推送 | Commit 推送自动生成图片通知 |
| `SetProjectInfo` | 项目信息 | 设置 Bot 状态信息 |
| `SizeNtUid` | 账号信息查询 | `/info` |
| `AtriChat` | AI 聊天 | 关键词触发 AI 对话 |
| `AutoSendPtt` | 自动语音 | 定时发送语音消息 |
| `like/CardLike` | QQ 点赞 | 关键词触发、手动点赞 |
| `like/AutoLikeCommand` | 点赞管理 | `/autolike` |
| `personal/AnAnGirlEmoji` | 安安表情 | `/anan` |
| `personal/CucumberGirl` | 好女孩 | `/gt` |
| `personal/PinYin` | 拼音转换 | `/py` |
| `personal/MojiraStatus` | Mojira 漏洞追踪 | `/check-mojira` |
| `personal/EmailNotify` | 邮件通知 | IMAP 邮件监听 |
| `classtable/` | 课表查询 | 课表数据处理 |

### 官方机器人功能（`function/official/`）

| 类 | 功能 | 说明 |
|----|------|------|
| `EventRecord` | 事件处理 | 新成员欢迎、群注册/注销、指令拦截、黑名单 |
| `ChatContentRecord` | 消息记录 | 全平台消息存入 MySQL |
| `SignCommand` | 每日打卡 | `/打卡` |
| `CoinsCommand` | 金粒余额 | `/golds` / `/金粒` |
| `WhoAmI` | 身份查询 | `/whoami` |
| `GroupCommand` | 群白名单管理 | `/ogroup` |
| `UserMgrCommand` | 权限管理 | `/perm`（setrole / add / remove） |
| `ConnectFourGame` | 四子棋 | `/connect4` / `/四子棋` |
| `MinesweeperGame` | 扫雷 | `/minesweeper` / `/扫雷` |
| `RockPaperScissorsGame` | 石头剪刀布 | `/rsp` |
| `MiniGameCommand` | 小游戏菜单 | `/games` |
| `MusicCommand` | 音乐搜索 | `/music` |
| `PlayerProfile` | 玩家资料查询 | `/stats` |
| `RconHandler` | RCON 控制 | `/rc` |
| `VerifyMinecraftCommand` | MC 账号验证 | `/verify` |
| `PushTaskCommand` | 推送任务管理 | `/推送任务` |
| `FullMessageEnableCommand` | 全量消息授权 | `/全量消息` |
| `WebUICommand` | WebUI 管理 | `/webui` |
| `BanTracker` | Hypixel 封禁追踪 | `/bantracker` |
| `minecraft/MinecraftBind` | MC 账号绑定 | 数据库绑定管理 |
| `minecraft/MinecraftRemote` | MC 远程控制 | 通过 Minecraft Socket |
| `minecraft/MinecraftCapes` | MC Capes 查询 | `/mc capes` |
| `minecraft/PackMcmetaGenerator` | 资源包生成 | `/mc pack` |
| `tufe/ElectricCheck` | 宿舍电费查询 | `/elec` |
| `imagesource/ImageSubmitCommand` | 图源投稿 | `/投稿` |
| `imagesource/ImageSourceStatsCommand` | 图源统计 | `/图源` |
| `pushtask/` | 推送任务 | 日历、MC新闻、Hypixel新闻、欢迎等推送 |

### 定时任务（`function/task/`）

| 类 | 说明 |
|----|------|
| `MessageStats` | 消息统计 `/chat` |
| `ManosabaDate` | Manosaba 日期 |
| `Calendar` | 日历定时推送 |
| `AutoSign` | 自动 SA 签到 |
| `TufeClassAlert` | 课表提醒 `/tufe` |
| `CheckInExportTask` | 打卡数据导出 |

---

## Napcat 群组功能开关

Napcat 平台支持按群组独立开关功能，通过 `GroupConfigManager` 管理。每个群可独立控制以下功能：

| 功能键 | 默认值 | 说明 |
|--------|--------|------|
| `auto_sign` | true | 自动签到 |
| `mc_news` | false | MC 新闻推送 |
| `hyp_news` | false | Hypixel 新闻推送 |
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

通过 WebUI 或 `/groupinfo` 指令可查看和修改群组配置。

---

## WebUI

项目包含一个 Vue 3 + Vite 前端（源码在 `webui/` 目录），构建产物在 `src/main/resources/official-webui/`。启动后通过以下路径访问：

- **Napcat WebUI**：`http://host:port/webui`
- **官方机器人 WebUI**：`http://host:port/official-webui`

### WebUI 页面

| 页面 | 路径 | 功能 |
|------|------|------|
| Dashboard | `/` | 数据概览、DAU、消息统计 |
| 群聊管理 | `/` (嵌入) | 群消息查看、功能开关、发送消息、撤回 |
| C2C 私聊 | `/c2c` | 私聊消息查看、用户管理、发送消息 |
| 用户管理 | `/users` | 用户列表、权限管理、角色设置 |
| 反馈管理 | `/feedback` | 反馈列表、回复反馈 |
| 图源管理 | `/gallery` | 投稿图片审核、删除、统计 |
| 数据统计 | `/stats` | 消息趋势、DAU 图表 |
| 错误报告 | `/errors` | 错误日志查看、统计 |
| Napcat 管理 | `/napcat` | Napcat 群组功能开关、消息记录 |
| API 调试 | `/debug` | 官方 API 调试工具 |
| 登录 | `/login` | Token 鉴权登录 |

### 鉴权

WebUI 使用 Sa-Token Sign 进行 API 签名鉴权，登录 Token 在 `config.yml` 的 `official-webui-token` 中配置。会话通过 Cookie 管理。

---

## 公开 API

项目提供无需登录鉴权的公开查询接口，用于外部系统集成。详见 [`scripts/public_official_api.md`](scripts/public_official_api.md)。

基础路径：`/webui/api/public/official`

| 接口 | 说明 |
|------|------|
| `GET /group/messages/received` | 群聊接收消息计数 |
| `GET /group/messages/sent` | 群聊发送消息计数 |
| `GET /c2c/messages/received` | C2C 接收消息计数 |
| `GET /c2c/messages/sent` | C2C 发送消息计数 |
| `GET /dau` | DAU 和消息统计 |
| `GET /users/{userOpenId}` | 用户信息查询 |
| `GET /groups/{groupOpenId}` | 群聊信息查询 |

所有接口有 1 分钟内存缓存，支持 `start`/`end` 时间参数。

---

## 构建与运行

### 构建

```bash
./gradlew shadowJar
```

构建产物在 `build/libs/AtriMeow-<version>.jar`。

构建过程会自动生成 `git.properties`，包含构建时间、版本号、Git commit hash 和分支名。

### 关键依赖

| 依赖 | 用途 |
|------|------|
| Javalin 6.6 | HTTP 服务器（WebUI、Napcat 回调） |
| Java-WebSocket | QQ 官方机器人 / Discord WebSocket 网关 |
| HikariCP 7.1 + MySQL Connector 9.7 | 数据库连接池 |
| Jackson 2.22 | JSON 序列化 |
| SnakeYAML 2.6 | 配置文件解析 |
| Sa-Token Sign 1.45 | API 签名鉴权 |
| Guava 33.6 | 缓存（消息序号等） |
| JSoup 1.22 + Readability4J | 网页抓取与正文提取 |
| Lombok 1.18 | 代码生成（getter/setter/log 等） |
| Logback 1.5 | 日志框架 |
| Lunar 1.7 | 农历/日历计算 |
| Jpinyin 1.1 | 拼音转换 |
| Jakarta Mail 2.0 | IMAP 邮件监听 |
| Apache Commons Text 1.15 | 字符串工具 |
| Kotlin Stdlib 2.1 | Kotlin 标准库（部分功能使用 Kotlin 编写） |

### Kotlin 文件

项目中有少量 Kotlin 文件，主要集中在以下位置：

| 文件 | 说明 |
|------|------|
| `function/general/BoopCommand.kt` | Boop 指令 |
| `function/napcat/personal/AnAnGirlEmoji.kt` | 安安表情 |
| `function/napcat/personal/CucumberGirl.kt` | 好女孩 |
| `function/napcat/personal/PinYin.kt` | 拼音转换 |
| `event/EventType.kt` | 事件类型枚举 |

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
│   ├── Properties.java           #   配置属性
│   ├── ResourcesProperties.java  #   资源常量
│   └── LoadIllegalWords.java     #   敏感词加载
├── chat/
│   ├── napcat/                   # Napcat 消息发送门面
│   │   ├── GroupMessage.java     #   群聊发送
│   │   ├── PrivateMessage.java   #   私聊发送
│   │   ├── FriendList.java       #   好友列表
│   │   └── impl/
│   │       ├── MessageUtils.java #   底层实现（构建段、发 HTTP）
│   │       └── MessageSegment.java # 消息段记录
│   └── official/                 # 官方机器人消息发送门面
│       ├── GroupChat.java        #   群聊发送
│       ├── C2CChat.java          #   私聊发送
│       ├── ChatService.java      #   底层实现（HTTP API 调用）
│       ├── Markdown.java         #   Markdown 辅助构造
│       ├── TC.java               #   快捷工具（md/keyboard）
│       ├── MessageBody.java      #   消息体 DTO
│       ├── button/               #   按钮模型
│       └── media/                #   消息/图片类型枚举
├── event/                        # 事件系统
│   ├── EventManager.java         #   事件注册/派发
│   ├── EventHandler.java         #   @EventHandler 注解
│   ├── EventPriority.java        #   优先级枚举
│   ├── EventType.kt              #   事件类型枚举（Kotlin）
│   ├── Listener.java             #   监听器接口
│   ├── Cancellable.java          #   可取消接口
│   ├── events/                   #   所有事件类
│   └── impl/                     #   事件相关枚举
├── command/                      # 指令系统
│   ├── CommandManager.java       #   指令注册/分发
│   ├── CommandSender.java        #   指令发送者（多平台适配）
│   ├── DiscordSlashCommandSender.java # Discord Slash Command 发送者
│   └── ...
├── platform/                     # 平台抽象层
│   ├── Platform.java             #   平台枚举
│   ├── PlatformRole.java         #   平台角色枚举
│   ├── User.java                 #   用户模型
│   ├── Message.java              #   消息模型
│   ├── Identifier.java           #   标识符常量
│   ├── napcat/                   #   Napcat 平台
│   │   ├── RequestReceiver.java  #     HTTP 请求接收
│   │   └── groupfunction/        #     群组功能管理
│   ├── official/                 #   官方平台
│   │   ├── OfficialBot.java      #     Bot 信息
│   │   ├── OfficialManager.java  #     WebSocket 管理
│   │   └── TokenManager.java     #     Token 管理
│   └── discord/                  #   Discord 平台
│       ├── DiscordManager.java   #     Discord 管理
│       ├── DiscordWebSocketClient.java # WebSocket 客户端
│       ├── DiscordMessage.java   #     消息模型
│       ├── DiscordUser.java      #     用户模型
│       └── DiscordEvents.java    #     事件处理
├── function/                     # 功能模块
│   ├── general/                  #   通用功能
│   │   ├── impl/                 #     通用实现（文章抓取、图片生成等）
│   │   └── *.kt                  #     Kotlin 文件
│   ├── napcat/                   #   Napcat 专有功能
│   │   ├── like/                 #     点赞功能
│   │   ├── personal/             #     个人功能（表情、拼音等）
│   │   └── classtable/           #     课表查询
│   ├── official/                 #   官方机器人专有功能
│   │   ├── minecraft/            #     Minecraft 相关
│   │   ├── tufe/                 #     宿舍电费
│   │   ├── imagesource/          #     图源投稿
│   │   └── pushtask/             #     推送任务
│   ├── discord/                  #   Discord 专有功能
│   └── task/                     #   定时任务
├── service/                      # 服务层
│   ├── Scheduler.java            #   调度器
│   ├── ai/                       #   AI 服务（多提供商）
│   │   ├── AiService.java
│   │   ├── AiProvider.java
│   │   └── AiProperties.java
│   ├── email/                    #   邮件服务（IMAP）
│   ├── request/                  #   HTTP 请求服务
│   ├── runtime/                  #   线程管理
│   │   └── ThreadManager.java
│   ├── taskscheduler/            #   声明式任务调度
│   │   ├── TaskScheduler.java
│   │   ├── ScheduledTask.java
│   │   └── ScheduleMode.java
│   └── timer/                    #   定时任务扫描器
│       ├── RunScheduleTask.java
│       └── Schedule.java         #     @Schedule 注解
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
│       └── TufeElecRepository.java
├── auth/official/                # 权限管理
│   ├── OfficialUsers.java        #   用户角色管理
│   ├── OfficialGroups.java       #   群白名单管理
│   ├── PermissionRole.java       #   角色枚举（OWNER/ADMIN/USER）
│   └── FullMessageAuth.java      #   全量消息授权
├── webui/                        # WebUI 后端
│   ├── Result.java               #   统一响应格式
│   ├── repo/                     #   WebUI 仓库层
│   └── impl/
│       ├── WebUIRouter.java      #   路由注册
│       ├── WebUIController.java  #   控制器
│       ├── WebUISessionManager.java # 会话管理
│       └── SseBroadcaster.java   #   SSE 推送
└── utils/                        # 工具类
    ├── YamlConfiguration.java    #   YAML 配置工具
    ├── FormatTools.java          #   格式化工具
    ├── GetProjectInfo.java       #   项目信息
    ├── ErrorReport.java          #   错误上报
    ├── debug/                    #   调试工具
    ├── notify/                   #   通知服务
    │   ├── NotificationService.java
    │   └── PendingNoticeDispatcher.java
    ├── socket/                   #   Minecraft Socket
    │   └── MinecraftSocket.java
    ├── statistic/                #   运行时数据统计
    │   └── BotRuntimeData.java
    ├── tools/                    #   工具类
    │   ├── Alert.java
    │   └── RM.java
    └── update/                   #   更新推送
        └── UpdatePushCommand.java
```

---

## License

MIT License © 2026 YZ_Ljc_
