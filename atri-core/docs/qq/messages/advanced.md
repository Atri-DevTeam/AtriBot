# 自定义消息请求

[API 目录](../../README.md) · `ChatService` / `MessageBody`

`MessageBody` 用于组合正文、键盘、操作栏和引用，各组件直接通过 Jackson 序列化，不需要转换成 Map

业务发送使用字符串、Markdown、ImageComponent、Card 或 Ark 对应的发送方法，`MessageBody` 由底层发送管道组装

## MessageBody 字段

支持 `builder()`、`toBuilder()` 以及 Lombok 生成的 getter/setter，null 字段不序列化

| Java 字段 | 请求字段 | 说明 |
| --- | --- | --- |
| content | content | 普通文本 |
| msgType | msg_type | 可省略，由正文类型推导；显式填写时校验是否匹配 |
| markdown | markdown | Markdown 对象 |
| keyboard | keyboard | 发送层生成的 `KeyboardPayload` 协议快照 |
| promptKeyboard | prompt_keyboard | 输入提示键盘协议结构，仅私聊 |
| ark | ark | Ark 卡片对象，被动消息需私域机器人 |
| card | card | 图文卡片对象 |
| actionButton | action_button | `ActionButton` 消息操作栏对象，仅私聊 |
| media | media | `new Media(fileInfo)`，文件上传仍使用原有上传方法 |
| inputNotify | input_notify | 仅私聊，`new InputNotify(1, 5)` 显示 5 秒；取消使用 `new InputNotify(2, null)` |
| messageReference | message_reference | `new MessageReference(refIdx)`，不等同于被动回复来源 |
| msgId / eventId | msg_id / event_id | 被动回复来源 |
| msgSeq | msg_seq | 回复序号 |
| isWakeup | is_wakeup | C2C 召回标记 |
| image | image | QQ 频道图片地址 |
| embed | embed | QQ 频道 Embed 对象 |
| recordAttachments | 不发送 | 供记录层使用的附件信息 |
| maintenanceReply | 不发送 | 维护提示内部标记，普通业务不应设置 |

::: important 对象字段的序列化
正文使用 Markdown、Card、Ark 或 Embed 等组件对象。业务代码通过 `Markdown.setKeyboard(Keyboard)` 设置按钮，发送层生成独立的键盘协议快照。自行序列化时，可先调用 `normalized()` 整理消息类型和键盘；该方法不会分配回复序号
:::

输入状态通知使用 `sendPrivateInputNotifyAsync`，返回是否发送成功，不记录为聊天消息

## 消息副本与校验

`withReply(RT)` 返回替换回复来源后的副本，保留正文与引用，清除原有 `msg_id`、`event_id`、`msg_seq` 和 `is_wakeup` 后设置新来源

`normalized()` 推导消息类型、整理键盘并返回副本。以下组合会抛出 IllegalArgumentException：

- 同时设置非空的 `msg_id` 和 `event_id`
- 召回消息同时携带被动回复来源
- 显式设置的 `msg_seq` 小于 1
- 同时设置多个正文组件，或将频道图片与其他正文组件混用
- 未提供正文，或显式消息类型与正文组件不一致

发送层在需要时分配缺省回复序号，调用 `normalized()` 本身不会分配。`MessageReference` 只表示引用展示，不替代 `RT.message` 或 `RT.event` 的被动回复来源

## ChatService 方法

### 发送时附加操作栏

QQ 官方私聊的文本、Markdown 和 ImageComponent 发送方法支持在末尾追加 `ActionButton`。操作栏写入请求的 `action_button` 字段，回复来源和引用规则沿用原方法

::: important 仅私聊
群聊发送类与群聊专用事件不提供操作栏重载。`QQCommandSender` 和 `OfficialButtonInteractionEvent` 的共用入口保留此参数，群聊调用时忽略
:::

```java
var actions = new ActionButton().setFeedback(true).setTts(true);
sender.sendMessage(new Markdown("处理完成"), actions);
sender.sendMessage(new Markdown("处理完成"), false, actions);
c2cEvent.sendMessage("处理完成", actions);
friendAddEvent.sendOpeningMessage(new Markdown("欢迎使用"), actions);
interactionEvent.replyMessage(new Markdown("处理完成"), actions);
```

`actionButton` 为 null 时不额外设置操作栏。文件、语音、Ark、Card 和流式消息的专用发送方法不添加此参数

字段说明、发送入口和反馈事件示例见 [私聊消息操作栏](../components/action-button.md)

### 底层方法

| 方法 | 返回 | 用途 |
| --- | --- | --- |
| `sendGroupMessageAsync(groupOpenId, request)` | Future<String> | 群聊请求 |
| `sendPrivateMessageAsync(openId, request)` | Future<String> | C2C 请求 |
| `sendPrivateWakeupMessageAsync(openId, request)` | Future<String> | 设置召回状态后发送 |
| `sendPrivateInputNotifyAsync(openId, inputSecond)` | Future<Boolean> | 输入状态 |
| `sendGuildChannelMessageAsync(channelId, request)` | Future<String> | 子频道请求 |
| `sendGuildDirectMessageAsync(guildId, request)` | Future<String> | 频道私信请求 |
| `createGuildThreadAsync(channelId, thread)` | Future<String> | 发布帖子，参数实现 JsonPayload，返回任务 ID |
| `deleteGuildThreadAsync(channelId, threadId)` | Future<Boolean> | 删除帖子 |
| `recallGroupMessageAsync` / `recallPrivateMessageAsync` | Future<Boolean> | 群聊 / C2C 撤回 |
| `recallGroupMessage` / `recallPrivateMessage` | boolean | 直接同步执行底层撤回；群撤回需要队列时使用异步入口 |
| `sendMessageAsync(JsonPayload, url, logType)` | Future<?> | 当前固定使用 PUT，成功内容为 JsonNode，不是通用 POST 发送方法 |
| `getUserInfo(userOpenId, groupOpenId)` | String / null | 已弃用的成员原始 JSON 查询，改用 GroupMember |

`privateMessageUrl`、`privateFileUrl`、`groupMessageUrl`、`groupFileUrl`、`guildChannelMessageUrl`、`guildDirectMessageUrl`、`guildThreadCreateUrl`、`guildThreadDeleteUrl` 只拼接地址，不发请求

## 服务状态

`ChatService.isEmergencyPaused()` 和 `setEmergencyPaused(boolean)` 读取、修改全局暂停状态。暂停后普通发送可能被拒绝或替换为 Provider 提供的维护提示

Provider 负责 close；`ChatService.close()` 会关闭其群撤回队列。`shutdownGroupRecallQueue()` 是关闭当前服务队列的静态兼容入口，正常运行期间不应调用

`getTokenManager()` 提供令牌服务，`getApiBaseUrl()` 读取基础地址。bodyFactory、mediaUploader、privateStreamHelper 等返回包内类型的 getter 属于实现细节，不作为外部构造入口

`top.yzljc.atricore.utils.JsonPayload` 的 `toObject()` 提供可序列化对象，`toJson()` 生成 JSON 字符串；请求结构优先按对象组织

## 消息类型编号

`chat.official.media.GroupMessageType` 提供下列枚举，`getValue()` 读取编号，`getDesc()` 读取说明。枚举存在不表示所有会话都支持该类型，例如 Embed 的高层入口用于 QQ 频道

| 枚举 | 编号 |
| --- | --- |
| TEXT | 0 |
| MARKDOWN | 2 |
| ARK | 3 |
| EMBED | 4 |
| MEDIA | 7 |
| CARD | 8 |

## 发送前事件

`OfficialMessageSendNotifier.allowSend(method, url, requestBody)` 为自行编写官方消息传输入口时使用的同步事件桥接方法。参数依次为 HTTP 方法、完整 URL、JSON 请求快照；返回 false 时调用方应停止发送

它只对识别到的 POST 消息请求分发 `OfficialMessageSendEvent`，非消息请求直接返回 true。纯上传、输入状态通知不触发发送事件，带 `srv_send_msg=true` 的群聊 / C2C 上传请求会触发

现有聊天入口已接入此方法，业务监听 [OfficialMessageSendEvent](../../events.md) 即可，不要在正常发送前重复调用。该方法只分发事件，不执行 HTTP 请求

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### ChatService

```java
public ChatService(String apiBaseUrl, TokenManager tokenManager);
public String privateMessageUrl(String openId);
public String privateFileUrl(String openId);
public String groupMessageUrl(String groupOpenId);
public String groupFileUrl(String groupOpenId);
public String guildChannelMessageUrl(String channelId);
public String guildDirectMessageUrl(String guildId);
public String guildThreadCreateUrl(String channelId);
public String guildThreadDeleteUrl(String channelId, String threadId);
public CompletableFuture<String> sendPrivateMessageAsync(String openId, MessageBody request);
public CompletableFuture<String> sendPrivateWakeupMessageAsync(String openId, MessageBody request);
public CompletableFuture<Boolean> sendPrivateInputNotifyAsync(String openId, int inputSecond);
public CompletableFuture<String> sendGroupMessageAsync(String groupOpenId, MessageBody request);
public CompletableFuture<String> sendGuildChannelMessageAsync(String channelId, MessageBody request);
public CompletableFuture<String> sendGuildDirectMessageAsync(String guildId, MessageBody request);
public CompletableFuture<String> createGuildThreadAsync(String channelId, JsonPayload thread);
public CompletableFuture<Boolean> deleteGuildThreadAsync(String channelId, String threadId);
public CompletableFuture<Boolean> recallPrivateMessageAsync(String userOpenId, String messageId);
public boolean recallPrivateMessage(String userOpenId, String messageId);
public CompletableFuture<?> sendMessageAsync(JsonPayload request, String url, String logType);
public CompletableFuture<Boolean> recallGroupMessageAsync(String groupOpenId, String messageId);
public boolean recallGroupMessage(String groupOpenId, String messageId);
public static void shutdownGroupRecallQueue();
public void close();
public String getUserInfo(String userOpenId, String groupOpenId);
```
