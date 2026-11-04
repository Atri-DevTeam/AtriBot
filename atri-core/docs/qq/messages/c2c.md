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
| `replyMessage(openId, rt, content)` | 上述五种内容，必须有被动回复来源；Ark 需私域机器人 |
| `sendMessage(openId, markdown.setKeyboard(keyboard))` | 主动发送 Markdown 与键盘 |
| `replyMessage(openId, rt, markdown.setKeyboard(keyboard))` | 被动回复 Markdown 与键盘 |
| `sendMessage(openId, fileType, path)` | 上传并发送本地文件 |
| `replyMessage(openId, rt, fileType, path)` | 上传并回复本地文件 |
| `replyAudioMessage(openId, rt, url)` | 语音地址，上传失败不发送文本回退 |
| `recallMessage(openId, messageId)` | 撤回，返回 boolean 或 Future<Boolean> |

::: danger Ark 被动消息使用条件
Ark 被动消息需要私域机器人才能使用，同步与异步重载均适用，详见 [Ark 卡片](../components/ark.md)
:::

引用重载与群聊类似：`refMessage(openId, refIdx, text / image / markdown)`，键盘通过 `markdown.setKeyboard(keyboard)` 设置。文本、图片、Markdown 回复还有 refIdx 参数，完整重载见下方签名

## 消息操作栏

::: important 仅私聊
`ActionButton` 仅用于 QQ 官方机器人 C2C 私聊。字段、调用范围和操作反馈示例见 [私聊消息操作栏](../components/action-button.md)
:::

文本、Markdown 和 ImageComponent 的主动发送、被动回复、引用回复及召回消息入口，提供在原参数末尾追加 `ActionButton actionButton` 的重载，同步和异步入口保持一致

```java
import top.yzljc.atricore.chat.official.button.ActionButton;

var actionButton = new ActionButton().setFeedback(true).setTts(true).setCallbackData("query_result");
C2CChat.sendMessage(userOpenId, TC.md("查询完成"), actionButton);
C2CChat.replyMessage(userOpenId, RT.message(messageId), "查询完成", actionButton);
```

`actionButton` 为 null 时不额外设置操作栏。文件、语音、Ark、Card、流式消息、输入状态通知和撤回接口不提供此重载

::: tip 重载参数
无需引用或操作栏时使用参数更少的重载；显式传入空引用时使用 `(String) null`，避免与 ActionButton 重载产生歧义
:::

## 正在输入通知

`sendInputNotify(openId)` 默认持续 60 秒，`sendInputNotify(openId, inputSecond)` 显式设置大于 0 的秒数。返回 boolean 或 Future<Boolean>，表示此次状态请求是否成功，不返回聊天消息 ID

## 召回消息

| 方法 | 内容 |
| --- | --- |
| `wakeupMessage(openId, content)` | String、Markdown、ImageComponent、Ark、Card |
| `wakeupMessage(openId, markdown.setKeyboard(keyboard))` | Markdown 与按钮 |
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
public static String wakeupMessage(String openId, String text, ActionButton actionButton);
public static String wakeupMessage(String openId, Ark ark);
public static String wakeupMessage(String openId, Card card);
public static String wakeupMessage(String openId, Markdown markdown);
public static String wakeupMessage(String openId, Markdown markdown, ActionButton actionButton);
public static String wakeupMessage(String openId, ImageComponent image);
public static String wakeupMessage(String openId, ImageComponent image, ActionButton actionButton);
public static String wakeupMessage(String openId, FileType fileType, String url);
public static String wakeupAudioMessage(String openId, String url);
public static String wakeupTextStreamDeltas(String openId, List<String> textDeltas);
public static String wakeupStreamDeltas(String openId, List<Markdown> markdownDeltas);
public static String sendMessage(String openId, ImageComponent image, String refIdx);
public static String sendMessage(String openId, ImageComponent image, String refIdx, ActionButton actionButton);
public static String sendMessage(String openId, Markdown markdown, String refIdx);
public static String sendMessage(String openId, Markdown markdown, String refIdx, ActionButton actionButton);
public static String refMessage(String openId, String refIdx, ImageComponent image);
public static String refMessage(String openId, String refIdx, ImageComponent image, ActionButton actionButton);
public static String refMessage(String openId, String refIdx, Markdown markdown);
public static String refMessage(String openId, String refIdx, Markdown markdown, ActionButton actionButton);
public static String replyMessage(String openId, RT rt, Markdown markdown, String refIdx);
public static String replyMessage(String openId, RT rt, Markdown markdown, String refIdx, ActionButton actionButton);
public static String replyMessage(String openId, RT rt, ImageComponent image, String refIdx);
public static String replyMessage(String openId, RT rt, ImageComponent image, String refIdx, ActionButton actionButton);
public static String replyAudioMessage(String openId, RT rt, String url);
public static String sendMessage(String openId, String text);
public static String sendMessage(String openId, String text, ActionButton actionButton);
public static String sendMessage(String openId, Ark ark);
public static String sendMessage(String openId, Card card);
public static String sendMessage(String openId, Markdown markdown);
public static String sendMessage(String openId, Markdown markdown, ActionButton actionButton);
public static String sendMessage(String openId, ImageComponent image);
public static String sendMessage(String openId, ImageComponent image, ActionButton actionButton);
public static String replyMessage(String openId, RT rt, String replyText);
public static String replyMessage(String openId, RT rt, String replyText, ActionButton actionButton);
public static String replyMessage(String openId, RT rt, String replyText, String refIdx);
public static String replyMessage(String openId, RT rt, String replyText, String refIdx, ActionButton actionButton);
public static String replyMessage(String openId, RT rt, Ark ark);
public static String replyMessage(String openId, RT rt, Card card);
public static String refMessage(String openId, String refIdx, String content);
public static String refMessage(String openId, String refIdx, String content, ActionButton actionButton);
public static String replyMessage(String openId, RT rt, Markdown markdown);
public static String replyMessage(String openId, RT rt, Markdown markdown, ActionButton actionButton);
public static String replyMessage(String openId, RT rt, ImageComponent image);
public static String replyMessage(String openId, RT rt, ImageComponent image, ActionButton actionButton);
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
public static CompletableFuture<String> wakeupMessage(String openId, String text, ActionButton actionButton);
public static CompletableFuture<String> wakeupMessage(String openId, Ark ark);
public static CompletableFuture<String> wakeupMessage(String openId, Card card);
public static CompletableFuture<String> wakeupMessage(String openId, Markdown markdown);
public static CompletableFuture<String> wakeupMessage(String openId, Markdown markdown, ActionButton actionButton);
public static CompletableFuture<String> wakeupMessage(String openId, ImageComponent image);
public static CompletableFuture<String> wakeupMessage(String openId, ImageComponent image, ActionButton actionButton);
public static CompletableFuture<String> wakeupMessage(String openId, FileType fileType, String url);
public static CompletableFuture<String> wakeupAudioMessage(String openId, String url);
public static CompletableFuture<String> wakeupTextStreamDeltas(String openId, List<String> textDeltas);
public static CompletableFuture<String> wakeupStreamDeltas(String openId, List<Markdown> markdownDeltas);
public static CompletableFuture<String> sendMessage(String openId, ImageComponent image, String refIdx);
public static CompletableFuture<String> sendMessage(String openId, ImageComponent image, String refIdx, ActionButton actionButton);
public static CompletableFuture<String> sendMessage(String openId, Markdown markdown, String refIdx);
public static CompletableFuture<String> sendMessage(String openId, Markdown markdown, String refIdx, ActionButton actionButton);
public static CompletableFuture<String> refMessage(String openId, String refIdx, ImageComponent image);
public static CompletableFuture<String> refMessage(String openId, String refIdx, ImageComponent image, ActionButton actionButton);
public static CompletableFuture<String> refMessage(String openId, String refIdx, Markdown markdown);
public static CompletableFuture<String> refMessage(String openId, String refIdx, Markdown markdown, ActionButton actionButton);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown, String refIdx);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown, String refIdx, ActionButton actionButton);
public static CompletableFuture<String> replyMessage(String openId, RT rt, ImageComponent image, String refIdx);
public static CompletableFuture<String> replyMessage(String openId, RT rt, ImageComponent image, String refIdx, ActionButton actionButton);
public static CompletableFuture<String> replyAudioMessage(String openId, RT rt, String url);
public static CompletableFuture<String> sendMessage(String openId, String text);
public static CompletableFuture<String> sendMessage(String openId, String text, ActionButton actionButton);
public static CompletableFuture<String> sendMessage(String openId, Ark ark);
public static CompletableFuture<String> sendMessage(String openId, Card card);
public static CompletableFuture<String> sendMessage(String openId, Markdown markdown);
public static CompletableFuture<String> sendMessage(String openId, Markdown markdown, ActionButton actionButton);
public static CompletableFuture<String> sendMessage(String openId, ImageComponent image);
public static CompletableFuture<String> sendMessage(String openId, ImageComponent image, ActionButton actionButton);
public static CompletableFuture<String> replyMessage(String openId, RT rt, String replyText);
public static CompletableFuture<String> replyMessage(String openId, RT rt, String replyText, ActionButton actionButton);
public static CompletableFuture<String> replyMessage(String openId, RT rt, String replyText, String refIdx);
public static CompletableFuture<String> replyMessage(String openId, RT rt, String replyText, String refIdx, ActionButton actionButton);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Ark ark);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Card card);
public static CompletableFuture<String> refMessage(String openId, String refIdx, String content);
public static CompletableFuture<String> refMessage(String openId, String refIdx, String content, ActionButton actionButton);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown);
public static CompletableFuture<String> replyMessage(String openId, RT rt, Markdown markdown, ActionButton actionButton);
public static CompletableFuture<String> replyMessage(String openId, RT rt, ImageComponent image);
public static CompletableFuture<String> replyMessage(String openId, RT rt, ImageComponent image, ActionButton actionButton);
public static CompletableFuture<String> streamDeltas(String openId, List<Markdown> markdownDeltas);
public static CompletableFuture<String> replyStreamDeltas(String openId, RT rt, List<Markdown> markdownDeltas);
public static CompletableFuture<String> streamTextDeltas(String openId, List<String> textDeltas);
public static CompletableFuture<String> replyTextStreamDeltas(String openId, RT rt, List<String> textDeltas);
```
