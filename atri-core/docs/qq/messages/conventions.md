# QQ 消息调用约定

[API 目录](../../README.md)

调用前完成 [Provider 初始化与启动](../../getting-started.md)，启用 QQ 并配置连接信息。聊天类共享 Provider 管理的 ChatService 和 TokenManager，业务无需为每条消息创建服务

## 场景与标识

| 场景 | 同步入口 | 异步入口 | 目标标识 |
| --- | --- | --- | --- |
| 群聊 | GroupChat | AsyncGroupChat | 群 OpenID |
| C2C | C2CChat | AsyncC2CChat | 用户 OpenID |
| 文字子频道 | GuildChannelChat | AsyncGuildChannelChat | 子频道 ID |
| 频道私信 | GuildDirectChat | AsyncGuildDirectChat | 私信会话 guildId |

这些类均位于 `top.yzljc.atricore.chat.official`。QQ 号码、群号码、OpenID、频道 ID 不可相互替代

## 主动、被动和引用

| 入口 / 参数 | 用途 |
| --- | --- |
| `sendMessage` | 主动发送 |
| `replyMessage` | 使用消息或事件作为被动回复来源 |
| `RT.message(messageId)` | 写入 msg_id，并由框架处理回复序号 |
| `RT.event(eventId)` | 写入 event_id |
| `refIdx` | 可见引用的消息索引，与被动回复来源独立 |
| `refMessage` | 主动发送并附加可见引用，不携带被动回复来源 |

`RT` 是封闭接口，包含 `RT.Message` 和 `RT.Event`，通过 `id()` 读取来源标识。构造时拒绝空白 ID，被动接口不允许 rt 为 null

::: important 回复来源与引用索引
消息来源用于满足平台的被动回复条件，不保证客户端显示引用；需要同时引用时选择含 refIdx 的重载。撤回使用实际消息 ID，不使用引用索引
:::

## 同步与异步

同步方法内部等待 Future，仍会阻塞当前调用线程。异步方法通常返回 `CompletableFuture<String>`，成功结果为发送消息 ID，撤回和状态操作返回 `CompletableFuture<Boolean>`

```java
import top.yzljc.atricore.chat.official.AsyncGroupChat;
import top.yzljc.atricore.chat.official.RT;

AsyncGroupChat.replyMessage(groupOpenId, RT.message(messageId), "处理完成")
        .whenComplete((sentId, failure) -> {
            if (failure != null) {
                System.err.println("发送异常: " + failure.getMessage());
            } else if (sentId == null) {
                System.err.println("未获得消息 ID");
            } else {
                System.out.println(sentId);
            }
        });
```

::: important 异步发送的结果判断
Future 已创建不代表平台已接收消息。参数校验可能在创建 Future 前抛出异常；后台请求失败可能异常完成，也可能得到 null / false
:::

## 异常与发送记录

`QQMessageSendException` 提供 `getMessage()`、`getCode()`、`getTraceId()`，后两个字段在平台未提供或解析失败时可以为 null。`fromResponse(mapper, responseBody, fallbackMessage)` 可将平台错误响应转为该异常

GroupChat、C2CChat 的同步方法重新抛出等待过程中产生的 QQMessageSendException，其他异步执行异常通常转为空结果；频道同步入口会将等待失败转为空结果。中断时恢复线程中断标记

发送记录由 `LogRecorder` 接入，HTTP 成功后仍可能执行运行服务提供的记录逻辑；不能将异步发送理解为所有记录回调均在独立队列处理

调用资格、主动消息额度、媒体限制仍以平台响应为准，各文档中的批次大小与阈值描述的是当前 core 实现

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### QQMessageSendException

```java
public static QQMessageSendException fromResponse(ObjectMapper objectMapper, String responseBody, String fallbackMessage);
```
