# 自定义消息请求

[API 目录](../../README.md) · `ChatService` / `MessageBody`

高层聊天方法覆盖常见发送场景；需要设置额外字段时，从 Provider 获取现有 ChatService，不另行创建或关闭聊天服务

```java
import top.yzljc.atricore.AtriProvider;
import top.yzljc.atricore.chat.official.MessageBody;

var request = MessageBody.builder()
        .msgType(0)
        .content("处理完成")
        .msgId(messageId)
        .msgSeq(1)
        .build();
var future = AtriProvider.getInstance().getChatService()
        .sendGroupMessageAsync(groupOpenId, request);
```

::: caution 手动管理回复序号
手动构造请求时由调用方负责消息类型、回复来源、序号和组件之间的配合。同一来源重复回复不应始终使用固定序号，常规回复优先使用 GroupChat 等高层方法
:::

## MessageBody 字段

支持 `builder()`、`toBuilder()` 以及 Lombok 生成的 getter/setter，null 字段不序列化

| Java 字段 | 请求字段 | 说明 |
| --- | --- | --- |
| content | content | 普通文本 |
| msgType | msg_type | 消息类型编号 |
| markdown | markdown | Markdown 对象 |
| keyboard | keyboard | 按钮键盘对象 |
| promptKeyboard | prompt_keyboard | C2C 提示键盘对象 |
| ark | ark | Ark 卡片对象，被动消息需私域机器人 |
| card | card | 图文卡片对象 |
| media | media | 上传后的媒体信息 |
| inputNotify | input_notify | 输入状态对象 |
| messageReference | message_reference | 可见引用对象 |
| msgId / eventId | msg_id / event_id | 被动回复来源 |
| msgSeq | msg_seq | 回复序号 |
| isWakeup | is_wakeup | C2C 召回标记 |
| image | image | QQ 频道图片地址 |
| embed | embed | QQ 频道 Embed 对象 |
| recordAttachments | 不发送 | 供记录层使用的附件信息 |
| maintenanceReply | 不发送 | 维护提示内部标记，普通业务不应设置 |

::: important 对象字段的序列化
Object 字段可使用 DTO 或 Map；已经序列化的 JSON 字符串仍会作为字符串发送。Card 和 Ark 放入字段时使用各自 `toObject()`，Embed 使用 `toPayload()`，键盘使用 `toKeyboardObject()`
:::

## ChatService 方法

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
