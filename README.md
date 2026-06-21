# AtriMeow

基于 Java 的多平台 QQ 机器人，同时适配 **Napcat（OneBot 协议）** 与 **QQ 官方机器人 API**，注意到，本项目的一些写法与`Bukkit`高度相似

免责声明：`撰写者为什么都不会的新手，且部分复杂逻辑使用了AIGC，说人话就是这是屎山，仅部分内容能参考一下（虽然但是，我的屎山能跑！！！）`

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
- [功能模块](#功能模块)
- [WebUI](#webui)
- [构建与运行](#构建与运行)

---

## 平台架构

项目同时运行两套适配层，共享同一套事件系统和指令系统：

| 层级 | Napcat（OneBot） | QQ 官方机器人 |
|------|------------------|---------------|
| 入口 | HTTP POST `127.0.0.1:port/` | WebSocket 网关连接 |
| 消息发送 | `chat/napcat/` | `chat/official/` |
| 消息类型 | 文本、图片(URL/BASE64/FILE)、@、回复、合并转发 | 文本、Markdown、图片(MEDIA)、文件、键盘按钮 |
| 事件类 | `Napcat*Event` | `Official*Event` |
| 功能类 | `function/napcat/` | `function/official/` |

`Napcat` 的网络接口是`HTTP`模式

两套平台通过 `CommandManager` 统一处理指令，通过 `EventManager` 统一派发事件

---

## 快速开始

### 环境要求

- JDK 21+
- MySQL 数据库
- Napcat 服务端（使用 OneBot 时）或 QQ 开放平台 AppID/Secret（使用官方机器人时）

### 运行

1. 将 `src/main/resources/config.yml` 复制到 jar 同级目录，按需填写（见下方[配置文件](#配置文件)）
2. `java -jar AtriMeow.jar`
3. 控制台输入 `stop` 可安全关闭。

---

## 配置文件

配置文件为 jar 同级目录下的 `config.yml`，首次运行会自动生成带默认值的模板。以下为必要字段说明：

### 全局设置

```yaml
command-prefix: "/"       # 指令前缀，OneBot 和官方机器人通用
debug-mode: false         # Debug 模式，开启后部分功能行为变化
listen-port: 1234         # HTTP 服务器监听端口（WebUI、Napcat 回调）

mysql:                    # MySQL 数据库连接（必填）
  host: "localhost"
  port: 3306
  database: "database"
  username: "root"
  password: "null"

ai:                       # AI 服务配置（部分功能使用）
  api-key: ""
  base-url: ""            # 需包含 /v1/chat/completions
  model: "qwen3.5-flash"
  timeout: 30000
```

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
```

### 功能配置

```yaml
function:
  github-webhook:           # GitHub Webhook（Commit 推送）
    port: 54321
    secret: "your-secret"
  bilibili-cookie: ""       # B 站视频解析用的 Cookie
  wakeup-image-link: ""     # 叫醒表情包链接
  keywords-hitokoto:        # 触发"一言"的关键词
    - "一言"
  keywords-like-user:       # 触发"点赞"的关键词
    - "点赞"
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

事件系统是统一的消息/通知处理框架，两套平台的底层事件都会经过它，后续其他平台的扩展也将在这里完成

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

指令通过 `CommandManager` 统一管理，同时监听 Napcat 和官方机器人的消息事件

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
        
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true; // 对于执行官机群聊、私聊、第三方机器人的指令时，必须对不同平台的行为进行约束，避免抛出`UnsupportedOperationException`
        
        sender.sendMessage("你执行了 /" + label + "，参数：" + String.join(" ", args));
        return true; // return false 将发送默认的指令使用帮助
    }
}
```

#### 3. 在 `Atri.java` 的 `onEnable()` 中的指令注册方式

```java
CommandManager.getCommand("mycommand").setExecutor(new MyCommandExecutor());
```

### CommandSender 常用方法

```java
// sender 自动适配平台（Napcat 群/私聊、官方群/私聊）
sender.sendMessage("文本回复");              // 自动选择正确的发送方式
sender.hasPermission();                      // 检查是否管理员
sender.getPlatform();                        // 获取平台枚举
sender.getUserId();                          // 获取用户 ID
sender.getGroupId();                         // 获取群 ID（私聊时为 null）
sender.getMentions();                        // 获取消息中 @ 的用户列表
```

---

## 延迟执行与异步任务

项目提供 `Scheduler`（`service/Scheduler.java`）和 `ThreadManager`（`service/runtime/ThreadManager.java`）两套工具来执行延迟和异步任务

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

### 使用建议

- **简单延迟/定时**：用 `Scheduler`，通过 `Atri.getInstance().getScheduler()` 获取
- **需要链式异步处理**：用 `ThreadManager.supplyAsync()` 获取 `CompletableFuture`
- **一次性 fire-and-forget**：用 `ThreadManager.execute()`
- 所有任务执行都有异常捕获和日志记录，不会因未捕获异常导致线程崩溃

---

## 功能模块

### 通用功能（`function/general/`）

两套平台均可使用的功能：

| 类 | 功能 | 触发方式 |
|----|------|----------|
| `HelpCommand` | 帮助菜单 | `/help` |
| `SponsorCommand` | 贡献名单 | `/贡献名单` |
| `Hitokoto` | 随机一言 | `/hitokoto` 或关键词触发 |
| `MojangStatus` | Mojang 服务状态 | `/mojang` |
| `HappyNewYear` | 新年倒计时 | `/newyear` |
| `HypixelReward` | 领取 Hypixel 奖励 | `/cl` |
| `MinecraftCommand` | MC 子命令（骰子、版本查询） | `/mc` |
| `MinecraftNews` | MC 新闻推送 | `/checkmcnews` |
| `Calendar` | 每日日历 | `/calendar` / `/today` |
| `Feedback` | 反馈提交 | `/feedback` |
| `HypixelAnnouncements` | Hypixel 公告推送 | `/check-hyp` |

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
| `Motd` | MC 服务器状态 | `/motd` |
| `Broadcast` | 全服广播 | `/bc` |
| `RollbackMessages` | 批量撤回 | `/rollback` |
| `AutoPokeBack` | 自动回戳 | 被戳自动反戳 + 发图 |
| `github/WebhookServer` | GitHub 推送 | Commit 推送自动生成图片通知 |
| `like/LikeUser` | QQ 点赞 | 关键词触发、手动点赞、自动点赞 |
| `like/AutoLikeCommand` | 点赞管理 | `/autolike` |

### 官方机器人功能（`function/official/`）

| 类 | 功能 | 说明 |
|----|------|------|
| `EventRecord` | 事件处理 | 新成员欢迎、群注册/注销、指令拦截、黑名单 |
| `ChatContentRecord` | 消息记录 | 全平台消息存入 MySQL |
| `SignCommand` | 每日打卡 | `/打卡` |
| `WhoAmI` | 身份查询 | `/whoami` |
| `GroupCommand` | 群白名单管理 | `/ogroup` |
| `PermissionCommand` | 权限管理 | `/permission` |
| `ConnectFourGame` | 四子棋 | `/connect4` / `/四子棋` |
| `MinesweeperGame` | 扫雷 | `/minesweeper` / `/扫雷` |
| `MiniGameCommand` | 小游戏菜单 | `/games` |
| `MusicCommand` | 音乐搜索 | `/skb` |
| `PlayerProfile` | 玩家资料查询 | `/stats` |
| `RconHandler` | RCON 控制 | `/rc` |
| `VerifyMinecraftCommand` | MC 账号验证 | `/verify` |
| `PushTaskCommand` | 推送任务管理 | `/推送任务` |
| `FullMessageEnableCommand` | 全量消息授权 | `/全量消息` |
| `minecraft/MinecraftBind` | MC 账号绑定 | 数据库绑定管理 |
| `tufe/ElectricCheck` | 宿舍电费查询 | `/elec` |

### 定时任务（`function/task/`）

| 类 | 说明 |
|----|------|
| `ScheduledLikeUser` | 定时自动点赞 |
| `ScheduledCheckMcVersion` | MC 版本更新检测 |
| `ScheduledMinecraftNews` | MC 新闻定时推送 |
| `ScheduledHypixelNews` | Hypixel 新闻定时推送 |
| `ScheduledReboot` | 定时重启 |
| `MessageStats` | 消息统计 `/chat` |
| `ManosabaDate` | Manosaba 日期 |
| `Calendar` | 日历定时推送 |
| `AutoSign` | 自动 SA 签到 |
| `TufeClassAlert` | 课表提醒 `/tufe` |

---

## WebUI

项目包含一个 Vue 3 + Vite 前端，构建产物在 `src/main/resources/official-webui/`。启动后通过以下路径访问：

- **Napcat WebUI**：`http://host:port/webui`
- **官方机器人 WebUI**：`http://host:port/official-webui`

*官方机器人的webui开箱即用，Napcat的后端Webui需要自己搭建，尚未合并*

WebUI 提供消息记录查看、群管理、用户权限管理等功能，需要登录 Token（`config.yml` 中的 `official-webui-token`）鉴权

---

## 构建与运行

### 构建

```bash
./gradlew shadowJar
```

构建产物在 `build/libs/AtriMeow-3.1.0-Release.jar`。

### 关键依赖

| 依赖 | 用途 |
|------|------|
| Javalin | HTTP 服务器（WebUI、Napcat 回调） |
| Java-WebSocket | QQ 官方机器人 WebSocket 网关 |
| HikariCP + MySQL Connector | 数据库连接池 |
| Jackson | JSON 序列化 |
| SnakeYAML | 配置文件解析 |
| Sa-Token Sign | API 签名 |
| Guava | 缓存（消息序号等） |
| JSoup + Readability4J | 网页抓取与正文提取 |

---

## 项目结构

```
src/main/java/top/yzljc/atribot/
├── Atri.java                     # 主入口，初始化和生命周期管理
├── configuration/                # 配置类（Config, Properties, AiProperties）
├── chat/
│   ├── napcat/                   # Napcat 消息发送门面
│   │   ├── GroupMessage.java     #   群聊发送
│   │   ├── PrivateMessage.java   #   私聊发送
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
│   ├── Listener.java             #   监听器接口
│   ├── Cancellable.java          #   可取消接口
│   ├── events/                   #   所有事件类
│   └── impl/                     #   事件相关枚举
├── command/                      # 指令系统
│   ├── CommandManager.java       #   指令注册/分发
│   ├── CommandSender.java        #   指令发送者（多平台适配）
│   └── ...
├── platform/                     # 平台抽象层
│   ├── napcat/                   #   Napcat 平台（消息模型、HTTP 发送、请求接收）
│   └── official/                 #   官方平台（WebSocket 客户端、Token 管理）
├── function/                     # 功能模块
│   ├── general/                  #   通用功能
│   ├── napcat/                   #   Napcat 专有功能
│   ├── official/                 #   官方机器人专有功能
│   └── task/                     #   定时任务
├── service/                      # 服务层（AI、HTTP、调度、线程池）
├── database/                     # 数据库（连接池、仓库）
├── auth/official/                # 权限管理（用户角色、群白名单）
├── webui/                        # WebUI 后端（路由、SSE、会话管理）
└── utils/                        # 工具类
```

---

## License

MIT License © 2026 YZ_Ljc_
