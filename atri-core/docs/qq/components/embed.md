# QQ 频道 Embed

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.Embed`

Embed 用于 QQ 文字子频道与频道私信，当前聊天封装提供被动回复入口

```java
import top.yzljc.atricore.chat.official.Embed;
import top.yzljc.atricore.chat.official.GuildChannelChat;
import top.yzljc.atricore.chat.official.RT;
import java.util.List;

var embed = new Embed("查询结果", "收到查询结果",
        "https://example.com/thumbnail.png", List.of("等级：10", "状态：在线"));
String sentId = GuildChannelChat.replyMessage(channelId, RT.message(messageId), embed);
```

| 构造参数 | 类型 | 含义 |
| --- | --- | --- |
| title | String | 标题 |
| prompt | String | 提示文本 |
| imageUrl | String | 缩略图地址 |
| fields | List<String> | 按顺序显示的字段文本，每项转换为 name 字段 |

`getTitle()`、`getPrompt()`、`getImageUrl()`、`getFields()` 读取内容，`toPayload()` 返回平台对象。构造参数使用非 null 值，字段列表可以为空

频道私信使用 `GuildDirectChat.replyMessage(guildId, rt, embed)`，目标为私信会话的 guildId。异步调用对应 `AsyncGuildChannelChat`、`AsyncGuildDirectChat`

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### Embed

```java
public Map<String, Object> toPayload();
```
