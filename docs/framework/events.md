# 事件

[文档目录](../README.md) · [平台与消息](platforms.md)

本页说明监听器的注册与分发规则，并列出各事件的触发场景、主要数据和可用操作。事件类位于 [event/events](../../src/main/java/top/yzljc/atribot/event/events)

| 分类 | 内容 |
| --- | --- |
| [框架事件](#框架事件) | 文本指令执行 |
| [QQ 群聊与 C2C](#qq-群聊与-c2c) | 收取消息、好友关系、机器人与群成员变动、入群申请 |
| [QQ 频道](#qq-频道) | 子频道消息、频道私信、帖子、评论与回复 |
| [QQ 交互与发送](#qq-交互与发送) | 按钮、授权变更、发送审核与发送失败 |
| [Napcat / OneBot](#napcat--onebot) | 消息、请求、成员变动、戳一戳与撤回 |
| [Discord](#discord) | 消息与 Slash Command |
| [KOOK](#kook) | 频道消息、私信、按钮与系统通知 |
| [邮件](#邮件) | 新邮件与解析结果 |

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

当前支持取消的事件如下，其余事件不实现 `Cancellable`：

| 事件 | 取消效果 |
| --- | --- |
| `UserRunCommandEvent` | 分发结束时仍为取消状态，则不执行本次文本指令 |
| `OfficialMessageSendEvent` | 分发结束时仍为取消状态，则阻止本次消息请求 |
| `OfficialButtonInteractionEvent` | 取消后调用 `answer()` 会改为失败应答；取消本身不发送应答，也不阻止直接调用发送方法 |

## 线程与耗时处理

事件管理器不切换线程，也不保证所有事件都在同一线程执行。平台入口可能先将数据提交到工作队列，再调用 `callEvent()`；同一监听器可能同时处理多个事件

网络请求、长时间计算和数据库批处理会阻塞当前事件线程。需要异步处理时，先提取必要数据，再提交到受控执行器。不能在异步任务中延迟修改取消状态，并期望影响已经结束的同步分发

任务异常、队列容量及生命周期见 [运行时服务](runtime.md)

## 框架事件

### UserRunCommandEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/UserRunCommandEvent.java) · 文本指令匹配成功、即将交给执行器时触发

- 数据：`sender` 为指令发送者，`command` 为匹配到的指令，`label` 为分发标签，`args` 为参数数组。当前 `CommandMap` 为 `commandHeader` 和 `label` 传入相同值
- 操作：`setCancelled(true)` 阻止本次指令执行
- 范围：经过 `CommandMap` 的文本指令，包括无前缀触发词；Discord Slash Command 使用独立入口，不触发此事件

指令声明、别名及两种执行器见 [指令](commands.md)

## QQ 群聊与 C2C

QQ 平台上报事件由 [BotEvents](../../src/main/java/top/yzljc/atribot/platform/qq/BotEvents.java) 构建，WebSocket 与 Webhook 共用分发入口。是否能收到某类事件取决于平台授权及订阅配置

以下数据项使用事件类的 getter 读取，例如 `groupId` 对应 `getGroupId()`。`user`、`message` 保留平台各自的类型，字段含义见 [平台与消息](platforms.md)

### OfficialGroupAtMessageCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGroupAtMessageCreateEvent.java) · 收到 `GROUP_AT_MESSAGE_CREATE`，即群内 @ 机器人的消息时触发

- 数据：`QQUser user`、`QQMessage message`、`groupId`、`timestamp`
- 操作：`sendMessage()` 使用当前消息 ID 被动回复，支持文本或 Markdown；`shouldIgnore()` 检查群黑名单与用户封禁状态
- 范围：与全量群消息事件分别分发，监听其中一个不会自动收到另一个

### OfficialGroupMessageCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGroupMessageCreateEvent.java) · 收到全量群消息 `GROUP_MESSAGE_CREATE` 时触发

- 数据：`QQUser user`、`QQMessage message`、`groupId`、`timestamp`；`isAtBot()` 表示消息是否 @ 机器人
- 操作：`sendMessage()` 被动回复文本、图片、Markdown 或附带按钮的 Markdown；`shouldIgnore()` 检查群黑名单与用户封禁状态
- 判断：`isEmptyMessage()` 仅在 @ 机器人时检查去除开头提及后的文本是否为空，不是通用的空消息判断

### OfficialC2CMessageCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialC2CMessageCreateEvent.java) · 收到私聊消息 `C2C_MESSAGE_CREATE` 时触发

- 数据：`QQUser user`、`QQMessage message`、`timestamp`；`switchButtons` 保存解析出的开关按钮状态，未携带时可能为 `null`
- 操作：`sendMessage()` 被动回复文本或 Markdown，可附带按钮；`sendStreamMarkdownMessageD()`、`sendStreamTextMessageD()` 按增量列表发送流式回复
- 判断：`shouldIgnore()` 检查用户封禁状态；开关数据存在时，可通过 `switchButtons.isEnabled(key)` 查询指定开关

### OfficialFriendAddEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialFriendAddEvent.java) · 收到 `FRIEND_ADD`，即用户添加机器人好友时触发

- 数据：`eventId`、`userOpenId`、`timestamp`、添加来源 `scene`、来源参数 `sceneParam`、`shortCode`
- 操作：`sendOpeningMessage()` 使用本次事件 ID 发送文本或 Markdown 开场消息

### OfficialFriendDelEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialFriendDelEvent.java) · 收到 `FRIEND_DEL`，即用户删除机器人好友时触发

- 数据：`userOpenId`、`timestamp`
- 用途：更新好友关系或清理相关业务状态；事件不提供发送或恢复好友关系的方法

### OfficialGroupAddRobotEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGroupAddRobotEvent.java) · 收到 `GROUP_ADD_ROBOT`，即机器人被加入群聊时触发

- 数据：`eventId`、`groupOpenId`、操作成员 `opMemberOpenId`、`timestamp`
- 操作：`sendOpeningMessage()` 使用本次事件 ID 向群发送文本、Markdown 或附带按钮的 Markdown

### OfficialGroupDelRobotEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGroupDelRobotEvent.java) · 收到 `GROUP_DEL_ROBOT`，即机器人被移出群聊时触发

- 数据：`groupOpenId`、操作成员 `opMemberOpenId`、`timestamp`
- 用途：更新机器人所在群列表及群相关业务状态

### OfficialGroupMemberAddEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGroupMemberAddEvent.java) · 收到 `GROUP_MEMBER_ADD`，即群成员加入时触发

- 数据：`eventId`、`groupOpenId`、`memberOpenId`、`timestamp`
- 操作：`sendMessage()` 使用事件 ID 发送 Markdown，可附带按钮或 @ 新成员
- 身份：`getUserBotRole()` 返回成员在机器人系统中的 `UnifiedRole`，不是其 QQ 群内角色

### OfficialGroupMemberRemoveEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGroupMemberRemoveEvent.java) · 收到 `GROUP_MEMBER_REMOVE`，即群成员离开时触发

- 数据：`eventId`、`groupOpenId`、`memberOpenId`、`timestamp`
- 身份：`getUserBotRole()` 返回成员在机器人系统中的角色；事件未封装离群原因或操作人字段

### OfficialGroupJoinRequestEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGroupJoinRequestEvent.java) · 收到入群申请 `GROUP_JOIN_REQUEST` 时触发

- 数据：`eventId`、`groupOpenId`、`memberOpenId`、`username`、`joinRequestId`、`applyTime`、申请来源 `applySource`、验证方式 `method`
- 验证：`invitedBy` 为邀请人，`verifyMessage` 为验证文本，`verifyQAList` 为问答列表，`strategyId` 为自动同意策略 ID；这些字段按上报内容填充
- 操作：`approve()` 同意申请，`deny()` 拒绝申请；拒绝方法可指定原因及是否拉黑，返回接口操作结果
- 判断：`getQuestion()`、`getAnswer()` 提供首条问答或验证文本的快捷读取；使用问答方式时应先确认列表非空

## QQ 频道

频道消息使用 `QQGuildUser` 与 `QQGuildMessage`，与 QQ 群聊、C2C 的对象分开。帖子相关事件直接提供作者 ID 和帖子数据，不构造聊天消息对象

### OfficialGuildAtMessageCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGuildAtMessageCreateEvent.java) · 收到文字子频道 @ 消息 `AT_MESSAGE_CREATE` 时触发

- 数据：`QQGuildUser user`、`QQGuildMessage message`、`guildId`、`channelId`、`userOpenId`；其中 `userOpenId` 取自上报的 `union_openid`，不同于频道用户 ID
- 操作：`replyMessage()` 使用当前子频道和消息 ID 回复文本或图片
- 判断：`isEmptyMessage()` 检查消息文本是否为空白

### OfficialGuildDirectMessageCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGuildDirectMessageCreateEvent.java) · 收到频道私信 `DIRECT_MESSAGE_CREATE` 时触发

- 数据：`QQGuildUser user`、`QQGuildMessage message`、`guildId`、`channelId`、取自 `union_openid` 的 `userOpenId`
- 操作：`replyMessage()` 使用私信会话的 `guildId` 和当前消息 ID 回复文本或图片

### OfficialAtForumThreadCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialAtForumThreadCreateEvent.java) · 收到 `AT_FORUM_THREAD_CREATE`，即创建帖子并 @ 机器人时触发

- 数据：`guildId`、`channelId`、`authorId`、`ThreadInfo threadInfo`；帖子数据包含 `threadId`、`title`、`content`、`dateTime`
- 解析：`threadInfo.getTitleAsRichText()`、`getContentAsRichText()` 将原始 JSON 字符串解析为 `RichText`；格式不合法时抛出异常
- 操作：`createThread(GuildThread)` 在当前板块发表新帖子，等待接口返回发表任务 ID。该 ID 不是帖子 ID，发送与删除接口见 [频道帖子](platforms.md#频道帖子)

### OfficialAtForumPostCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialAtForumPostCreateEvent.java) · 收到 `AT_FORUM_POST_CREATE`，即创建帖子评论并 @ 机器人时触发

- 数据：`guildId`、`channelId`、`authorId`、`PostInfo postInfo`；评论数据包含所属 `threadId`、`postId`、`content`、`dateTime`、原帖作者 `threadAuthorId`
- 解析：`postInfo.getContentAsRichText()` 解析评论正文；事件本身不提供发表评论的方法

### OfficialAtForumReplyCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialAtForumReplyCreateEvent.java) · 收到 `AT_FORUM_REPLY_CREATE`，即创建评论回复并 @ 机器人时触发

- 数据：`guildId`、`channelId`、`authorId`、`ReplyInfo replyInfo`；回复数据包含所属 `threadId`、`postId`、`replyId`、`content`、`dateTime`
- 解析：`replyInfo.getContentAsRichText()` 解析回复正文；事件本身不提供发送评论回复的方法

`ThreadInfo`、`PostInfo`、`ReplyInfo` 均为 record，使用 `threadId()`、`content()` 等访问原始字段，标题和正文在接收时保留原始字符串。富文本结构与构建方式见 [频道帖子](platforms.md#频道帖子)

## QQ 交互与发送

### OfficialInteractionEvents

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialInteractionEvents.java) · QQ 交互事件的抽象基类，不单独分发

- 数据：`applicationId`、上报事件 ID `eventId`、交互 ID `id`、`scene`、`timestamp`、交互类型 `type`、`version`
- 范围：按钮点击和 C2C 授权变更继承此类。事件管理器按具体类型匹配，监听该基类不能接收两个子类的事件

### OfficialButtonInteractionEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialButtonInteractionEvent.java) · 收到 `INTERACTION_CREATE` 中的按钮点击交互时触发

- 数据：公共交互字段，以及 `chatType`、`groupOpenId`、`userOpenId`、`data`；`data` 保留 `resolved` JSON 与内部类型
- 操作：`getButtonId()`、`getButtonValue()` 读取按钮标识和值；缺失时返回 `missing_data`。`answer(AnswerCode)` 向平台提交交互应答
- 回复：`replyMessage()` 使用事件 ID 被动回复，`sendMessage()` 主动发送；当前封装处理 `chatType = 1` 的群聊及 `chatType = 2` 的 C2C，其他场景抛出异常
- 过滤：`shouldIgnore()` 检查群黑名单、用户封禁和忽略状态，命中时同时提交失败应答。`setCancelled(true)` 本身不提交应答，后续调用 `answer()` 时才强制使用失败状态

### OfficialC2CAuthorizeModifyEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialC2CAuthorizeModifyEvent.java) · 收到 `INTERACTION_CREATE` 中的用户授权变更时触发

- 数据：公共交互字段，以及 `userOpenId`、`authorizeData`、操作场景 `optScene`、授权范围 `scope`
- 判断：`authorizeData` 保留 `optScene`、`scope` 原始字符串及 `switchData`；`isAllowedC2CPush()` 读取该开关状态
- 范围：当前枚举识别 `setting` 和 `c2c_push`，未知值对应枚举为 `null`。使用开关前应确认授权范围

### OfficialMessageSendEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialMessageSendEvent.java) · QQ 官方消息请求发出前同步触发，用于发送审核

- 数据：`platform`、目标 `targetId`、请求 `url`、JSON 字符串快照 `requestBody`、流式标记 `stream`
- 操作：`setCancelled(true)` 阻止本次请求；请求体是只读快照，解析并修改副本不会改变实际发送内容
- 范围：覆盖群聊、C2C、频道消息和频道私信；C2C 流式更新逐次触发。纯上传与输入状态通知不触发，带 `srv_send_msg = true` 的群聊或 C2C 文件请求参与审核。频道帖子接口不属于此事件的匹配范围

### OfficialGroupSendFailEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialGroupSendFailEvent.java) · 群消息发送接口返回无主动消息权限错误时触发

- 数据：`groupOpenId`、`errorCode`、`errorMessage`；`getErrorCodeEnum()` 将错误码映射为 `ErrorCode`
- 范围：当前 `ChatService` 仅在错误码为 `40034105` 时分发，不是所有群消息发送失败的统一通知

### OfficialC2CSendFailEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/OfficialC2CSendFailEvent.java) · C2C 普通消息或流式消息发送失败时，按各自发送入口的条件触发

- 数据：`userId`、`errorCode`、`errorMessage`
- 范围：普通消息入口仅在错误码为 `40034105` 时分发；流式入口会在失败响应可解析且能提取用户 ID 时分发，不限于该错误码。不能据此假定所有网络异常都会产生事件

## Napcat / OneBot

事件由 [RequestReceiver](../../src/main/java/top/yzljc/atribot/platform/napcat/RequestReceiver.java) 根据 HTTP 上报数据构建，用户与消息类型为 `NapcatUser`、`NapcatMessage`

### NapcatGroupMessageEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/NapcatGroupMessageEvent.java) · 收到 OneBot 群消息上报时触发

- 数据：`user`、`message`、`groupId`、`timestamp`
- 操作：`sendMessage()` 支持文本或 `List<MessageSegment>`；`recall()` 调用当前消息的撤回方法并返回结果

### NapcatPrivateMessageEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/NapcatPrivateMessageEvent.java) · 收到 OneBot 私聊消息上报时触发

- 数据：`user`、`message`、`timestamp`
- 操作：事件本身不封装发送方法，通过 `getUser()` 或对应聊天接口回复，通过 `getMessage().recall()` 撤回当前消息

### NapcatGroupMemberChangeEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/NapcatGroupMemberChangeEvent.java) · 用于接收群成员变动通知

- 数据：`time`、机器人 `selfId`、`groupId`、成员 `userId`、操作人 `operatorId`、`subType`、枚举 `operateType`
- 判断：枚举区分批准入群、主动退群、被踢、受邀入群、机器人受邀和机器人被踢；机器人受邀的 `subType` 被转换为 `invite_me`
- 操作：`sendMessage()` 向对应群发送文本
- 范围：当前入口会将带 `group_id` 且不是 `group_recall` 的 notice 均送入此事件，未知子类型的 `operateType` 可能为 `null`，监听器应先确认类型

### NapcatGroupRequestEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/NapcatGroupRequestEvent.java) · 收到 `request_type = group` 的入群申请或群邀请时触发

- 数据：`time`、`selfId`、`groupId`、`userId`、请求标识 `flag`、请求子类型 `subType`、附言 `comment`
- 操作：`reject()` 拒绝请求，可指定原因；事件未封装同意方法

### NapcatFriendRequestEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/NapcatFriendRequestEvent.java) · 收到 `request_type = friend` 的好友申请时触发

- 数据：`time`、`selfId`、申请人 `userId`、请求标识 `flag`
- 操作：`sendMessage()` 向申请人发送私聊文本；该方法不处理好友申请，也不代表同意申请

### NapcatPokedEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/NapcatPokedEvent.java) · 收到 `notice_type = notify`、`sub_type = poke` 的戳一戳通知时触发，入口排除机器人自身发起的操作

- 数据：`time`、`selfId`、被戳目标 `targetId`、发起人 `userId`、`groupId`
- 操作：`pokeBack()` 戳回发起人，`poke(userId)` 改为戳指定用户
- 范围：事件不要求被戳目标是机器人；仅处理戳机器人的逻辑时，应先比较 `targetId` 与 `selfId`

### NapcatRecallMessageEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/NapcatRecallMessageEvent.java) · 收到 `group_recall` 或 `friend_recall` 通知时，按配置范围触发

- 数据：`time`、`selfId`、`groupId`、原消息用户 `userId`、操作人 `operatorId`、`messageId`、撤回类型 `type`
- 范围：群撤回只对 `napcatMessageSpyGroups` 配置中的群分发，好友撤回不受该群列表限制
- 用途：通知消息已经被撤回；事件不包含原始正文，也不能取消已发生的撤回

## Discord

Gateway 入口将消息与指令交互提交到工作线程后分发，事件中的用户类型为 `DiscordUser`

### DiscordMessageCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/DiscordMessageCreateEvent.java) · 收到 Gateway 的 `MESSAGE_CREATE` 时触发

- 数据：`user`、`DiscordMessage message`、`guildId`、`channelId`；私信的 `guildId` 可为空
- 操作：事件不提供回复快捷方法，通过用户或平台聊天接口发送
- 范围：消息事件与 Slash Command 分开；收到文本消息本身不等于执行指令

### DiscordSlashCommandEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/DiscordSlashCommandEvent.java) · 收到 `INTERACTION_CREATE` 且 `type = 2` 的应用指令交互时触发

- 数据：`user`、`applicationId`、`interactionId`、`token`、`guildId`、`channelId`、`commandName`、`timestamp`、原始 `raw` 数据
- 参数：`options`、`resolved` 保留平台参数，`getArgs()` 提供 `DiscordSlashCommandArguments` 的结构化读取
- 操作：`reply()` 首次调用发送交互回调，后续调用发送 follow-up；`followUp()` 直接发送后续消息。当前两者返回交互 ID，不是新消息 ID
- 范围：不支持通过 `Cancellable` 取消；执行逻辑使用 Slash Command 执行器，不经过 `UserRunCommandEvent`

## KOOK

事件由 [KookManager](../../src/main/java/top/yzljc/atribot/platform/kook/KookManager.java) 处理 Webhook 数据后构建。普通消息入口过滤机器人用户及当前机器人自身的消息

### KookChannelMessageCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/KookChannelMessageCreateEvent.java) · 收到非系统消息且 `channel_type = GROUP` 时触发

- 数据：`KookUser user`、`KookMessage message`、`KookCommandSender sender`；频道及服务器信息由消息对象提供
- 操作：`replyMessage(text)` 委托 `sender.sendMessage(text)` 向当前频道发送文本；图片、卡片等通过 `getSender()` 使用

### KookDirectMessageCreateEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/KookDirectMessageCreateEvent.java) · 收到非系统消息且 `channel_type = PERSON` 时触发

- 数据：`KookUser user`、`KookMessage message`、`KookCommandSender sender`
- 操作：`replyMessage(text)` 委托 `sender.sendMessage(text)` 向当前私信对象发送文本；其他发送能力通过 `getSender()` 使用

### KookButtonClickEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/KookButtonClickEvent.java) · 收到系统消息 `type = 255` 且 `extra.type = message_btn_click` 时触发

- 数据：`raw` 保留上报的 `d` 对象；`getUserId()`、`getMessageId()`、`getTargetId()`、`getValue()` 从 `extra.body` 读取点击者、消息、目标和值
- 范围：回调按钮交由监听器处理，不会自动将按钮值作为文本指令执行，也不会再分发一次 `KookSystemEvent`

### KookSystemEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/KookSystemEvent.java) · 收到 `type = 255` 的其他系统通知时触发

- 数据：`type` 为 `extra.type` 字符串，`raw` 为上报的 `d` 对象
- 用途：处理尚未独立封装的系统通知；监听器根据 `type` 识别事件，再读取对应的 `extra.body`

## 邮件

### EmailMessageEvent

[源码](../../src/main/java/top/yzljc/atribot/event/events/EmailMessageEvent.java) · IMAP 收件流程分发新邮件时触发，构造事件时解析邮件内容

- 数据：原始 Jakarta Mail `message`、主题 `subject`、发件人 `authors`、收件人 `toRecipients`、抄送 `ccRecipients`、密送 `bccRecipients`、`sentDate`、`receivedDate`、`contentType`、`multipart`
- 正文：`plainText`、`htmlText`、最多 600 字符的 `contentSummary`，以及用于预览的 `bodyBlocks`
- 附件：`attachmentFileNames` 只列出文件名；`inlineImages` 保存可读取的内嵌图片，单张限制 6 MiB、总量限制 16 MiB，不包含普通附件内容
- 判断：`readError` 记录正文解析或部分内容读取问题，存在错误时仍可能取得已成功解析的数据
