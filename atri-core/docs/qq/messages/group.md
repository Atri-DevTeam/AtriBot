# QQ 群消息

[API 目录](../../README.md) · `GroupChat` / `AsyncGroupChat`

## 基本调用

```java
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.TC;

String sentId = GroupChat.sendMessage(groupOpenId, "通知内容");
String replyId = GroupChat.replyMessage(groupOpenId, RT.message(messageId),
        TC.md("**处理完成**"));
```

参数与失败处理遵循 [消息调用约定](conventions.md)，下表中的 target 均为 groupOpenId

## 发送能力

| 内容 | 主动发送 | 被动回复 |
| --- | --- | --- |
| String 文本 | `sendMessage(target, text)` | `replyMessage(target, rt, text)` |
| Markdown | `sendMessage(target, markdown)` | `replyMessage(target, rt, markdown)` |
| Markdown 与键盘 | `sendMessage(target, markdown, keyboard)` | `replyMessage(target, rt, markdown, keyboard)` |
| ImageComponent 图片 | `sendMessage(target, image)` | `replyMessage(target, rt, image)` |
| Ark | `sendMessage(target, ark)` | `replyMessage(target, rt, ark)`，需私域机器人 |
| Card | `sendMessage(target, card)` | `replyMessage(target, rt, card)` |
| 本地媒体文件 | `sendMessage(target, fileType, path)` | `replyMessage(target, rt, fileType, path)` |
| 媒体地址 | — | `replyMessage(target, rt, fileType, value)` |
| 语音地址 | — | `replyAudioMessage(target, rt, url)` |

以上发送方法返回 String 消息 ID，异步类使用相同参数返回 CompletableFuture。媒体与图片上传见 [文件上传](media.md)

::: danger Ark 被动消息使用条件
Ark 被动消息需要私域机器人才能使用，同步与异步重载均适用，详见 [Ark 卡片](../components/ark.md)
:::

## 提及与引用

| 调用 | 效果 |
| --- | --- |
| `replyMessage(groupOpenId, rt, userOpenId, markdown)` | 回复并提及目标用户 |
| `replyMessage(groupOpenId, rt, userOpenId, markdown, keyboard)` | 提及、Markdown 和键盘 |
| `replyMessage(groupOpenId, rt, userOpenId, markdown, keyboard, refIdx)` | 在上述基础上附加引用 |
| `replyMessage(groupOpenId, rt, text, refIdx)` | 文本回复并引用 |
| `replyMessage(groupOpenId, rt, image, refIdx)` | 图片回复并引用 |
| `replyMessage(groupOpenId, rt, markdown, keyboard, refIdx)` | Markdown 回复并引用 |
| `sendMessage(groupOpenId, image, refIdx)` | 主动图片消息并引用 |
| `sendMessage(groupOpenId, markdown, keyboard, refIdx)` | 主动 Markdown 消息并引用 |
| `refMessage(groupOpenId, refIdx, text / image / markdown)` | 主动引用消息 |
| `refMessage(groupOpenId, refIdx, markdown, keyboard)` | 主动引用并带键盘 |

`userOpenId` 是被提及者，`refIdx` 是引用索引，两者不替代 rt 中的来源 ID

## 撤回

```java
import top.yzljc.atricore.chat.official.AsyncGroupChat;

AsyncGroupChat.recallMessage(groupOpenId, sentId)
        .thenAccept(success -> System.out.println("撤回结果: " + success));
```

`GroupChat.recallMessage` 同步返回 boolean。异步撤回使用 ChatService 管理的队列，当前容量 1024，限制每秒 10 次；排队或执行失败需要处理 Future 的异常结果。同步入口也会等待该队列

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### GroupChat

```java
public static String sendMessage(String openId, FileType fileType, Path file);
public static String replyMessage(String openId, RT rt, FileType fileType, Path file);
public static String sendMessage(String groupOpenId, ImageComponent image, String refIdx);
public static String sendMessage(String groupOpenId, Markdown markdown, Object keyboard, String refIdx);
public static String refMessage(String groupOpenId, String refIdx, ImageComponent image);
public static String refMessage(String groupOpenId, String refIdx, Markdown markdown);
public static String refMessage(String groupOpenId, String refIdx, Markdown markdown, Object keyboard);
public static String replyMessage(String groupOpenId, RT rt, Markdown markdown, Object keyboard, String refIdx);
public static String replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown, Object keyboard, String refIdx);
public static String replyMessage(String groupOpenId, RT rt, ImageComponent image, String refIdx);
public static String sendMessage(String groupOpenId, String text);
public static String sendMessage(String groupOpenId, Ark ark);
public static String sendMessage(String groupOpenId, Card card);
public static String sendMessage(String groupOpenId, Markdown markdown);
public static String sendMessage(String groupOpenId, ImageComponent image);
public static String sendMessage(String groupOpenId, Markdown markdown, Object keyboard);
public static String replyMessage(String groupOpenId, RT rt, String replyText);
public static String replyMessage(String groupOpenId, RT rt, Ark ark);
public static String replyMessage(String groupOpenId, RT rt, Card card);
public static String replyMessage(String groupOpenId, RT rt, String replyText, String refIdx);
public static String replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown);
public static String replyMessage(String groupOpenId, RT rt, Markdown markdown);
public static String replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown, Object keyboard);
public static String replyMessage(String groupOpenId, RT rt, Markdown markdown, Object keyboard);
public static String replyMessage(String groupOpenId, RT rt, ImageComponent image);
public static String replyMessage(String groupOpenId, RT rt, FileType fileType, String value);
public static String replyAudioMessage(String groupOpenId, RT rt, String url);
public static String refMessage(String groupOpenId, String refIdx, String content);
public static boolean recallMessage(String groupOpenId, String messageId);
```

### AsyncGroupChat

```java
public static CompletableFuture<String> sendMessage(String openId, FileType fileType, Path file);
public static CompletableFuture<String> replyMessage(String openId, RT rt, FileType fileType, Path file);
public static CompletableFuture<Boolean> recallMessage(String groupOpenId, String messageId);
public static CompletableFuture<String> sendMessage(String groupOpenId, ImageComponent image, String refIdx);
public static CompletableFuture<String> sendMessage(String groupOpenId, Markdown markdown, Object keyboard, String refIdx);
public static CompletableFuture<String> refMessage(String groupOpenId, String refIdx, ImageComponent image);
public static CompletableFuture<String> refMessage(String groupOpenId, String refIdx, Markdown markdown);
public static CompletableFuture<String> refMessage(String groupOpenId, String refIdx, Markdown markdown, Object keyboard);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Markdown markdown, Object keyboard, String refIdx);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown, Object keyboard, String refIdx);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, ImageComponent image, String refIdx);
public static CompletableFuture<String> sendMessage(String groupOpenId, String text);
public static CompletableFuture<String> sendMessage(String groupOpenId, Ark ark);
public static CompletableFuture<String> sendMessage(String groupOpenId, Card card);
public static CompletableFuture<String> sendMessage(String groupOpenId, Markdown markdown);
public static CompletableFuture<String> sendMessage(String groupOpenId, ImageComponent image);
public static CompletableFuture<String> sendMessage(String groupOpenId, Markdown markdown, Object keyboard);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String replyText);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Ark ark);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Card card);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String replyText, String refIdx);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Markdown markdown);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, String userOpenId, Markdown markdown, Object keyboard);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, Markdown markdown, Object keyboard);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, ImageComponent image);
public static CompletableFuture<String> replyMessage(String groupOpenId, RT rt, FileType fileType, String value);
public static CompletableFuture<String> replyAudioMessage(String groupOpenId, RT rt, String url);
public static CompletableFuture<String> refMessage(String groupOpenId, String refIdx, String content);
```
