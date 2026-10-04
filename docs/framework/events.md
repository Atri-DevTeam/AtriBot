# 事件

[文档目录](../README.md) · [平台与消息](platforms.md)

## 注册监听器

监听器实现 `Listener`，处理方法标记 `@EventHandler`，方法只能有一个继承自 `Event` 的参数：

```java
package example;

import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;

public final class GroupListener implements Listener {
    @EventHandler
    public void onMessage(OfficialGroupMessageCreateEvent event) {
        if (event.shouldIgnore()) return;
        if ("ping".equals(event.getMessage().getContent().trim())) {
            event.sendMessage("pong");
        }
    }
}
```

核心注册方式：

```java
EventManager.getInstance().registerEvents(new GroupListener());
```

插件应使用 `getContext().registerEvents(listener)`，由上下文在插件停用时注销。核心手动注销使用 `unregisterEvents(listener)`，需要传入同一监听器实例

## 分发规则

[EventManager](../../src/main/java/top/yzljc/atribot/event/EventManager.java) 的行为如下：

| 项目 | 行为 |
| --- | --- |
| 方法扫描 | 当前监听器类的 `getDeclaredMethods()`，不自动扫描父类处理方法 |
| 类型匹配 | 按事件的具体运行时类型精确匹配 |
| 执行线程 | `callEvent()` 所在线程，同步执行 |
| 执行顺序 | 按优先级从低到高 |
| 异常 | 反射调用中的处理器异常被记录，继续处理后续监听器 |
| 业务过滤 | 由监听器检查，例如 `shouldIgnore()` |

监听 `Event` 基类不会收到全部子类事件。同一优先级下不要依赖处理器的先后次序

## 优先级与取消

优先级顺序：

```text
LOWEST → LOW → NORMAL → HIGH → HIGHEST → MONITOR
```

默认优先级为 `NORMAL`。`MONITOR` 是最后一个优先级，不带强制只读限制

只有实现 `Cancellable` 的事件支持取消。`ignoreCancelled = true` 表示跳过已经取消的事件，默认值为 `false`

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onCommand(UserRunCommandEvent event) {
    // 按业务判断是否调用 event.setCancelled(true)
}
```

取消标记由事件生产者决定如何处理。例如文本指令分发检查 `UserRunCommandEvent` 的取消状态后决定是否执行命令；取消某个事件不会自动撤回消息或停止所有业务

## 平台事件

| 场景 | 主要事件 |
| --- | --- |
| QQ 群 @ 消息 | `OfficialGroupAtMessageCreateEvent` |
| QQ 群全量消息 | `OfficialGroupMessageCreateEvent` |
| QQ C2C | `OfficialC2CMessageCreateEvent` |
| QQ 频道 @ 消息 | `OfficialGuildAtMessageCreateEvent` |
| QQ 频道私信 | `OfficialGuildDirectMessageCreateEvent` |
| QQ 频道帖子 @ 机器人 | `OfficialAtForumThreadCreateEvent` |
| Napcat 群聊 / 私聊 | `NapcatGroupMessageEvent`、`NapcatPrivateMessageEvent` |
| Discord 指令交互 | `DiscordSlashCommandEvent` |
| KOOK 频道 / 私信 | `KookChannelMessageCreateEvent`、`KookDirectMessageCreateEvent` |
| KOOK 按钮 / 系统 | `KookButtonClickEvent`、`KookSystemEvent` |

完整事件列表见 [event/events](../../src/main/java/top/yzljc/atribot/event/events)。不同事件提供各自的平台数据，不要求统一成一类消息事件

## 频道帖子事件

`AT_FORUM_THREAD_CREATE` 映射为 [OfficialAtForumThreadCreateEvent](../../src/main/java/top/yzljc/atribot/event/events/OfficialAtForumThreadCreateEvent.java)，WebSocket 和 Webhook 共用该分发逻辑。该事件继承 `Event`，不实现 `Cancellable`

| 访问方法 | 内容 |
| --- | --- |
| `event.getGuildId()` | 频道 ID |
| `event.getChannelId()` | 子频道板块 ID |
| `event.getAuthorId()` | 作者 ID |
| `event.getThreadInfo().threadId()` | 帖子 ID |
| `event.getThreadInfo().title()` | 原始标题字符串 |
| `event.getThreadInfo().content()` | 原始正文字符串 |
| `event.getThreadInfo().dateTime()` | 平台返回的时间字符串 |

标题和正文在接收时保留原值，需要富文本时再解析：

```java
@EventHandler
public void onThread(OfficialAtForumThreadCreateEvent event) {
    ThreadInfo info = event.getThreadInfo();
    RichText title = info.getTitleAsRichText();
    RichText content = info.getContentAsRichText();
}
```

`ThreadInfo` 和 `RichText` 位于 `chat.official.thread` 包。解析要求输入为合法的 RichText JSON，格式不合法时抛出异常，不会自动转换为纯文本

`event.createThread(thread)` 在当前板块发表新帖子，返回发表任务 ID，并等待接口结果。异步发表使用 `AsyncGuildChannelChat.createThread(event.getChannelId(), thread)`；删除使用 `AsyncGuildChannelChat.deleteThread(event.getChannelId(), event.getThreadInfo().threadId())`，详见 [频道帖子](platforms.md#频道帖子)

## 耗时处理

网络请求、长时间计算和数据库批处理会阻塞当前事件线程。需要异步处理时，先提取必要数据，再提交到受控执行器。不能在异步任务中延迟修改取消状态，并期望影响已经结束的同步分发

任务异常、队列容量及生命周期见 [运行时服务](runtime.md)
