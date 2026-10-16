# Provider

[文档目录](../README.md)

`AtriProvider` 是运行服务的抽象基类，提供框架服务访问和业务扩展方法

包名：`top.yzljc.atricore` · [源码](../src/main/java/top/yzljc/atricore/AtriProvider.java)

## 生命周期

```java
application.initialize();
application.getServer().get("/health", ctx -> ctx.result("ok"));
application.start();
```

`application` 为运行服务创建的 Provider 实现，完整入口见 [快速开始](getting-started.md)

### initialize

```java
void initialize()
void initialize(Consumer<JavalinConfig> configure)
```

注册当前实例，加载指令声明，注册指令监听器，创建 HTTP 服务及已启用的 QQ 基础服务，尚不监听端口或连接平台

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `configure` | `Consumer<JavalinConfig>` | Javalin 创建配置，用于静态资源、CORS、请求日志等，不得为 null |

无参重载使用默认 HTTP 配置，初始化仅允许执行一次

异常：重复初始化、实例已关闭或其他 Provider 正在使用时抛出 `IllegalStateException`，初始化失败会释放已创建的资源

### start

```java
void start()
```

获取 QQ 机器人资料，启动 HTTP 服务及已启用的平台连接，未调用 `initialize()` 时先按默认配置初始化

重复调用不重复启动，已关闭或同步启动步骤失败时抛出 `IllegalStateException`，失败时释放框架资源

::: note 启动与连接状态
WebSocket 连接握手异步执行，方法返回不表示连接已建立
:::

### close

```java
void close()
```

关闭 HTTP 服务、平台连接、回调队列、聊天队列、即时与延迟调度器及计划调度器，注销框架指令监听器，重复调用不重复关闭

已开始的计划任务不会被强制中断，进程共享线程通过 `ThreadManager.shutdown()` 单独关闭

## 获取服务

通过 `AtriProvider.getInstance()` 获取当前实例，未注册时抛出 `IllegalStateException`

| 方法 | 返回类型 | 说明 |
| --- | --- | --- |
| `getSettings()` | `AtriSettings` | 框架配置，默认读取 `CoreConfig` |
| `getServer()` | `Javalin` | 共享 HTTP 实例，初始化后用于注册路由 |
| `getScheduler()` | `Scheduler` | 当前 Provider 管理的即时、延迟与固定间隔调度器 |
| `getTaskScheduler()` | `TaskScheduler` | 当前 Provider 管理的计划调度器 |
| `getChatService()` | `ChatService` | QQ 聊天服务，仅 QQ 启用后可用 |
| `getTokenManager()` | `TokenManager` | QQ 连接和聊天共用的令牌服务 |
| `getAccessService()` | `AccessService` | 账号与权限查询 |
| `getLogRecorder()` | `LogRecorder` | 记录与通知回调 |
| `getBotCommand()` | `BotCommand` | 指令声明资源及业务策略 |
| `getMaintenanceMessage()` | `ImageComponent` | 维护状态回复图片，默认 null |

`getServer()`、`getChatService()`、`getTokenManager()` 在对应服务未初始化或关闭后抛出 `IllegalStateException`，`getScheduler()` 和 `getTaskScheduler()` 要求当前实例已注册且未关闭

`initialize()` 自动注册实例，常规接入无需调用 `setInstance(provider)`；后者仅注册服务引用，不创建或启动框架服务

## 业务扩展

基础服务由框架创建，运行服务只需覆盖业务相关方法

下面的示例通过记录器接收运行通知，其余扩展沿用默认行为

```java
package example;

import top.yzljc.atricore.AtriProvider;
import top.yzljc.atricore.core.LogRecorder;

public final class ServiceProvider extends AtriProvider {
    private final LogRecorder logRecorder = new LogRecorder() {
        @Override
        public void alert(String message) {
            System.getLogger(ServiceProvider.class.getName())
                    .log(System.Logger.Level.WARNING, message);
        }
    };

    @Override
    public LogRecorder getLogRecorder() {
        return logRecorder;
    }
}
```

::: important 扩展对象的生命周期
扩展对象应在 Provider 生命周期内复用，`getSettings()`、`getAccessService()`、`getLogRecorder()`、`getBotCommand()` 不得返回 null
:::

### AccessService

提供账号查询、权限判断和访问控制，默认不读取数据库、不授予权限、不保存状态

| 方法 | 返回类型 | 默认值 | 用途 |
| --- | --- | --- | --- |
| `findByQqUserOpenId(openId)` | `AtriAccount` | null | 按 QQ 用户 OpenID 查询账号 |
| `findByQqUserUin(uin)` | `AtriAccount` | null | 按 QQ 号码查询账号 |
| `isAdmin(userId)` | `boolean` | false | 判断机器人管理权限 |
| `hasPermission(userId, permission)` | `boolean` | false | 查询权限节点 |
| `isBlocked(userId)` | `boolean` | false | 查询封禁状态 |
| `isIgnored(userId)` | `boolean` | false | 查询忽略状态 |
| `isGroupBlacklisted(groupId)` | `boolean` | false | 查询群黑名单 |
| `getRole(userId)` | `UnifiedRole` | USER | 查询机器人账号角色 |
| `allowProactiveMsg(groupId)` | `boolean` | false | 查询 QQ 群主动消息设置 |
| `setAllowProactiveMsg(groupId, allowed)` | `void` | 不保存 | 更新主动消息设置 |

`userId` 使用调用平台的标识：QQ 群聊 / C2C 使用用户 OpenID，Napcat 使用 QQ 号码，频道权限查询使用统一用户标识，可能为 null

机器人管理权限与平台群主、管理员角色分别判断，账号绑定和权限缓存由运行服务实现

### LogRecorder

接收接口请求、平台事件、发送记录和运行通知，默认回调不执行操作

| 方法 | 用途 |
| --- | --- |
| `recordSend` | 记录请求，返回运行服务生成的关联标识，默认 null |
| `recordResponse` | 记录接口响应 |
| `recordError` | 记录接口调用或响应处理错误 |
| `recordEvent` | 记录 QQ 原始事件 |
| `recordSentGroupMessage` | 记录成功发送的 QQ 群消息 |
| `recordSentC2CMessage` | 记录成功发送的 QQ 私聊消息 |
| `napcatPacket` | 接收 Napcat 上报内容 |
| `qqEventDispatched` | 接收 QQ 事件分发后的数据 |
| `callGroupMessageSend`、`callPrivateMessageSend` | 统计 Napcat 发送调用 |
| `callRecallMessage` | 统计 Napcat 撤回调用 |
| `callCommandExecuted` | 统计平台消息进入指令分发 |
| `alert` | 接收框架运行通知 |
| `recordLastMsg` | 最近消息标识扩展点，当前 core 未调用 |

回调同步执行，可能并发调用，实现需保证线程安全并处理存储异常

请求回调中的 `traceId` 来自 `recordSend()`，与 QQ 响应的 `trace_id` 无关

Napcat 调用计数在请求前触发，指令分发计数也不表示业务执行成功，具体参数见 [LogRecorder](../src/main/java/top/yzljc/atricore/core/LogRecorder.java)

### BotCommand

| 方法 | 返回类型 | 默认值 | 用途 |
| --- | --- | --- | --- |
| `commandResource()` | `String` | `atribot.yml` | 指令声明资源路径 |
| `unknownCommandMessage()` | `String` | `未知指令` | 未知指令提示 |
| `disabledReason(commandName, groupId)` | `Optional<String>` | empty | QQ 群内指令禁用原因 |
| `isAdditionalCommand(content)` | `boolean` | false | 补充 `QQMessage.isCommand()` 判断 |

禁用原因非空时发送该文本并结束分发，`isAdditionalCommand()` 只影响判断，无前缀指令仍需通过 `prefixless-aliases` 注册
