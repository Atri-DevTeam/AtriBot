# QQ C2C 消息

[API 目录](../../README.md) · `C2CChat` / `AsyncC2CChat`

目标参数 openId 为用户 OpenID。同步返回 String 消息 ID，异步类使用相同参数返回 CompletableFuture，错误处理见 [调用约定](conventions.md)

## 文本与组件

```java
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.TC;

String sentId = C2CChat.replyMessage(userOpenId, RT.message(messageId),
        TC.md("**查询完成**"));
```

| 方法族 | 内容 |
| --- | --- |
| `sendMessage(openId, content)` | String、Markdown、ImageComponent、Ark、Card |
| `replyMessage(openId, rt, content)` | 上述五种内容，必须有被动回复来源 |
| `sendMessage(openId, markdown, keyboard)` | 主动发送 Markdown 与键盘 |
| `replyMessage(openId, rt, markdown, keyboard)` | 被动回复 Markdown 与键盘 |
| `sendMessage(openId, fileType, path)` | 上传并发送本地文件 |
| `replyMessage(openId, rt, fileType, path)` | 上传并回复本地文件 |
| `replyAudioMessage(openId, rt, url)` | 语音地址，上传失败不发送文本回退 |
| `recallMessage(openId, messageId)` | 撤回，返回 boolean 或 Future<Boolean> |

引用重载与群聊类似：`refMessage(openId, refIdx, text / image / markdown)`，以及附带 keyboard 的版本。文本、图片、Markdown 回复还有 refIdx 参数，完整重载见下方签名

## 正在输入

`sendInputNotify(openId)` 默认持续 60 秒，`sendInputNotify(openId, inputSecond)` 显式设置大于 0 的秒数。返回 boolean 或 Future<Boolean>，表示此次状态请求是否成功，不返回聊天消息 ID

## 召回消息

| 方法 | 内容 |
| --- | --- |
| `wakeupMessage(openId, content)` | String、Markdown、ImageComponent、Ark、Card |
| `wakeupMessage(openId, markdown, keyboard)` | Markdown 与按钮 |
| `wakeupMessage(openId, fileType, url)` | 远程媒体地址 |
| `wakeupAudioMessage(openId, url)` | 语音 |
| `wakeupTextStreamDeltas(openId, deltas)` | 文本增量列表 |
| `wakeupStreamDeltas(openId, deltas)` | Markdown 增量列表 |

召回入口设置 is_wakeup，并移除被动回复来源、序号和引用字段；实际发送资格由平台决定。召回消息与消息撤回是不同操作，删除已发送消息使用 recallMessage

流式消息的增量格式、回复来源和当前限制见 [C2C 流式消息](stream.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### C2CChat

```java
public static String sendMessage(String openId, FileType fileType, Path file);
public static String replyMessage(String openId, RT rt, FileType fileType, Path file);
public static boolean sendInputNotify(String openId);
public static boolean sendInputNotify(String openId, int inputSecond);
public static String wakeupMessage(String openId, String text);
public static String wakeupMessage(String openId, Ark ark);
public static String wakeupMessage(String openId, Card card);
public static String wakeupMessage(String openId, Markdown markdown);
public static String wakeupMessage(String openId, Markdown markdown, Object keyboard);
public static String wakeupMessage(String openId, ImageComponent image);
public static String wakeupMessage(String openId, FileType fileType, String url);
public static String wakeupAudioMessage(String openId, String url);
public static String wakeupTextStreamDeltas(String openId, List<String> textDeltas);
public static String wakeupStreamDeltas(String openId, List<Markdown> markdownDeltas);
public static String sendMessage(String openId, ImageComponent image, String refIdx);
public static String sendMessage(String openId, Markdown markdown, Object keyboard, String refIdx);
public static String refMessage(String openId, String refIdx, ImageComponent image);
public static String refMessage(String openId, String refIdx, Markdown markdown);
public static String refMessage(String openId, String refIdx, Markdown markdown, Object keyboard);
public static String replyMessage(String openId, RT rt, Markdown markdown, Object keyboard, String refIdx);
public static String replyMessage(String openId, RT rt, ImageComponent image, String refIdx);
public static String replyAudioMessage(String openId, RT rt, String url);
public static String sendMessage(String openId, String text);
public static String sendMessage(String openId, Ark ark);
public static String sendMessage(String openId, Card card);
public static String sendMessage(String openId, Markdown markdown);
public static String sendMessage(String openId, Markdown markdown, Object keyboard);
public static String sendMessage(String openId, ImageComponent image);
public static String replyMessage(String openId, RT rt, String replyText);
public static String replyMessage(String openId, RT rt, String replyText, String refIdx);
public static String replyMessage(String openId, RT rt, Ark ark);
public static String replyMessage(String openId, RT rt, Card card);
public static String refMessage(String openId, String refIdx, String content);
public static String replyMessage(String openId, RT rt, Markdown markdown);
public static String replyMessage(String openId, RT rt, Markdown markdown, Object keyboard);
public static String replyMessage(String openId, RT rt, ImageComponent image);
public static String streamDeltas(String openId, List<Markdown> markdownDeltas);
public static String replyStreamDeltas(String openId, RT rt, List<Markdown> markdownDeltas);
public static String streamTextDeltas(String openId, List<String> textDeltas);
public static String replyTextStreamDeltas(String openId, RT rt, List<String> textDeltas);
public static boolean recallMessage(String openId, String messageId);
```

### AsyncC2CChat

```java
public static CompletableFuture<String> sendMessage(String openId, FileType fileType, Path file);
public static CompletableFuture<String> replyMessage(String openId, RT rt, FileType fileType, Path file);
public static CompletableFuture<Boolean> recallMessage(String openId, String messageId);
public static CompletableFuture<Boolean> sendInputNotify(String openId);
public static CompletableFuture<Boolean> sendInputNotify(String openId, int inputSecond);
public static CompletableFuture<String> wakeupMessage(String openId, String text);
public static CompletableFuture<String> wakeupMessage(String openId, Ark ark);
public static CompletableFuture<String> wakeupMessage(String openId, Card card);
public static CompletableFuture<String> wakeupMessage(String openId, Markdown markdown);
public static CompletableFuture<String> wakeupMessage(String openId, Markdown markdown, Object keyboard);
public static CompletableFuture<String> wakeupMessage(String openId, ImageComponent image);
public static CompletableFuture<String> wakeupMessage(String openId, FileType fileType, String url);
public static CompletableFuture<String> wakeupAudioMessage(String openId, String url);
public static CompletableFuture<String> wakeupTextStreamDeltas(String openId, List<String> textDeltas);
public static CompletableFuture<String> wakeupStreamDeltas(String openId, List<Markdown> markdownDeltas);
public static CompletableFuture<String> sendMessage(String openId, ImageComponent image, String refIdx);
public static CompletableFuture<String> sendMessage(String openId, Markdown markdown, Object keyboard, String refIdx);
public static CompletableFuture<String> refMessage(String openId, String refIdx, ImageComponent image);
public static CompletableFuture<String> refMessage(String openId, String refIdx, Markdown markdown);
public static CompletableFuture<String> refMessage(String openId, String refIdx, Markdown markdown, Object keyboard);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown, Object keyboard, String refIdx);
public static CompletableFuture<String> replyMessage(String openId, RT rt, ImageComponent image, String refIdx);
public static CompletableFuture<String> replyAudioMessage(String openId, RT rt, String url);
public static CompletableFuture<String> sendMessage(String openId, String text);
public static CompletableFuture<String> sendMessage(String openId, Ark ark);
public static CompletableFuture<String> sendMessage(String openId, Card card);
public static CompletableFuture<String> sendMessage(String openId, Markdown markdown);
public static CompletableFuture<String> sendMessage(String openId, Markdown markdown, Object keyboard);
public static CompletableFuture<String> sendMessage(String openId, ImageComponent image);
public static CompletableFuture<String> replyMessage(String openId, RT rt, String replyText);
public static CompletableFuture<String> replyMessage(String openId, RT rt, String replyText, String refIdx);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Ark ark);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Card card);
public static CompletableFuture<String> refMessage(String openId, String refIdx, String content);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown, Object keyboard);
public static CompletableFuture<String> replyMessage(String openId, RT rt, ImageComponent image);
public static CompletableFuture<String> streamDeltas(String openId, List<Markdown> markdownDeltas);
public static CompletableFuture<String> replyStreamDeltas(String openId, RT rt, List<Markdown> markdownDeltas);
public static CompletableFuture<String> streamTextDeltas(String openId, List<String> textDeltas);
public static CompletableFuture<String> replyTextStreamDeltas(String openId, RT rt, List<String> textDeltas);
```
