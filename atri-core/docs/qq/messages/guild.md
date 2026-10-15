# QQ 频道消息

[API 目录](../../README.md)

文字子频道使用 `GuildChannelChat`，频道私信使用 `GuildDirectChat`。两者与 QQ 群聊 / C2C 分开提供入口

## 文字子频道

```java
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.GuildChannelChat;
import top.yzljc.atricore.chat.official.RT;

String sentId = GuildChannelChat.replyMessage(channelId, RT.message(messageId), "查询完成");
String imageId = GuildChannelChat.replyMessage(channelId, RT.message(messageId),
        ImageComponent.imageOf(imageUrl));
```

## 频道私信

```java
import top.yzljc.atricore.chat.official.GuildDirectChat;

String sentId = GuildDirectChat.replyMessage(dmGuildId, RT.message(messageId), "查询完成");
```

dmGuildId 来自私信会话，不是原频道 ID、子频道 ID 或用户 ID

| 方法 | 参数 |
| --- | --- |
| `GuildChannelChat.replyMessage(channelId, rt, text)` | 子频道、回复来源、文本 |
| `GuildChannelChat.replyMessage(channelId, rt, image)` | 图片组件 |
| `GuildChannelChat.replyMessage(channelId, rt, embed)` | [Embed](../components/embed.md) |
| `GuildDirectChat.replyMessage(guildId, rt, text / image / embed)` | 频道私信对应入口 |

异步入口分别为 `AsyncGuildChannelChat`、`AsyncGuildDirectChat`，返回 Future 中的消息 ID。同步等待失败返回 null

频道图片实现直接使用 ImageComponent.data 作为 image 字段，没有根据 ImageType 上传 Base64 或本地文件，应传平台可访问的 URL。附加文字通过 setText 设置

这两个高层聊天类当前只提供被动消息回复，没有与群聊完全相同的主动发送、键盘或撤回重载。发表与删除帖子见 [频道帖子](../forum/threads.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### GuildChannelChat

```java
public static String replyMessage(String channelId, RT rt, String text);
public static String replyMessage(String channelId, RT rt, ImageComponent image);
public static String replyMessage(String channelId, RT rt, Embed embed);
public static String createThread(String channelId, GuildThread thread);
public static boolean deleteThread(String channelId, String threadId);
```

### AsyncGuildChannelChat

```java
public static CompletableFuture<String> replyMessage(String channelId, RT rt, String text);
public static CompletableFuture<String> replyMessage(String channelId, RT rt, ImageComponent image);
public static CompletableFuture<String> replyMessage(String channelId, RT rt, Embed embed);
public static CompletableFuture<String> createThread(String channelId, GuildThread thread);
public static CompletableFuture<Boolean> deleteThread(String channelId, String threadId);
```

### GuildDirectChat

```java
public static String replyMessage(String guildId, RT rt, String text);
public static String replyMessage(String guildId, RT rt, ImageComponent image);
public static String replyMessage(String guildId, RT rt, Embed embed);
```

### AsyncGuildDirectChat

```java
public static CompletableFuture<String> replyMessage(String guildId, RT rt, String text);
public static CompletableFuture<String> replyMessage(String guildId, RT rt, ImageComponent image);
public static CompletableFuture<String> replyMessage(String guildId, RT rt, Embed embed);
```
