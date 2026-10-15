# 频道帖子

[API 目录](../../README.md) · 包 `top.yzljc.atricore.chat.official.thread`

## 发布富文本帖子

```java
import top.yzljc.atricore.chat.official.GuildChannelChat;
import top.yzljc.atricore.chat.official.thread.*;

var content = new RichText()
        .paragraph(new Paragraph().text("公告", TextStyle.BOLD))
        .paragraph(new Paragraph().text("本次更新内容"));
var thread = new GuildThread("更新公告", content);
String taskId = GuildChannelChat.createThread(channelId, thread);
```

| 方法 | 返回 | 参数 |
| --- | --- | --- |
| `GuildChannelChat.createThread(channelId, thread)` | String / null | 板块子频道 ID 与帖子对象 |
| `AsyncGuildChannelChat.createThread(channelId, thread)` | Future<String> | 异步发布 |
| `GuildChannelChat.deleteThread(channelId, threadId)` | boolean | 删除实际帖子 |
| `AsyncGuildChannelChat.deleteThread(channelId, threadId)` | Future<Boolean> | 异步删除 |

::: important 发布任务 ID 与帖子 ID
发表返回 **任务 ID**，不是 threadId；删除需使用查询或事件中的实际帖子 ID
:::

## GuildThread

`new GuildThread(title, richText)` 在构造时将正文保存为 JSON 字符串快照，后续修改 RichText 不会改变已经构造的帖子

`new GuildThread(title, content, format)` 可以显式选择内容格式

| Format | 值 | content 内容 |
| --- | --- | --- |
| FORMAT_TEXT | 1 | 普通文本 |
| FORMAT_HTML | 2 | HTML |
| FORMAT_MARKDOWN | 3 | Markdown |
| FORMAT_JSON | 4 | RichText JSON 字符串 |

`getTitle()`、`getContent()`、`getFormat()` 读取字段，`toObject()` 返回标题、正文字符串和格式编号

## 接收帖子、评论与回复

| 类型 | 内容 |
| --- | --- |
| `ThreadInfo` | 帖子 ID、标题、正文、时间；`getTitleAsRichText()`、`getContentAsRichText()` 解析内容 |
| `PostInfo` | 帖子 ID、评论 ID、正文、时间、帖子作者 ID；`getContentAsRichText()` |
| `ReplyInfo` | 帖子 ID、评论 ID、回复 ID、正文、时间；`getContentAsRichText()` |

事件分别为 `OfficialAtForumThreadCreateEvent`、`OfficialAtForumPostCreateEvent`、`OfficialAtForumReplyCreateEvent`，完整字段见 [事件](../../events.md)。富文本解析和构造见 [RichText](rich-text.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### GuildThread

```java
public GuildThread(String title, RichText content);
public Object toObject();
```

### ThreadInfo

```java
public RichText getTitleAsRichText();
public RichText getContentAsRichText();
```

### PostInfo

```java
public RichText getContentAsRichText();
```

### ReplyInfo

```java
public RichText getContentAsRichText();
```
