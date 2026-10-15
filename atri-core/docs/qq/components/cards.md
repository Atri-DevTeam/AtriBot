# 图文卡片

[API 目录](../../README.md) · 包 `top.yzljc.atricore.chat.official.card`

当前 `Card` 提供 `TuWen` 图文卡片，可通过群聊和 C2C 聊天接口发送

## 创建与发送

```java
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.card.Card;

Card card = Card.tuWen("查询结果", "点击查看详情",
        "https://example.com/cover.png", "https://example.com/detail");
String sentId = GroupChat.replyMessage(groupOpenId, RT.message(messageId), card);
```

| 参数 | 含义 |
| --- | --- |
| `title` | 卡片标题 |
| `description` | 描述 |
| `picUrl` | 封面图片地址 |
| `jumpUrl` | 点击跳转地址 |

四个参数均应提供非 null 值，底层 `Map.of` 不接受 null

## 对象与接口

| 入口 | 说明 |
| --- | --- |
| `Card.tuWen(title, description, picUrl, jumpUrl)` | 创建图文卡片 |
| `new TuWen(title, description, picUrl, jumpUrl)` | 创建卡片内容 |
| `new Card(content)` | 包装实现 CardContent 的内容 |
| `Card.getType()` | 返回 `Card.Type.TuWen` |
| `Card.getContent()` | 读取内容对象 |
| `Card.toObject()` | 返回包含 type 与 content 的平台对象 |
| `TuWen.toObject()` | 返回图文内容的 Map |
| `CardContent.getType()` / `toObject()` | 内容接口；自行扩展仍需平台支持 |

发送可选 `GroupChat`、`C2CChat` 的 `sendMessage(target, card)` 或 `replyMessage(target, rt, card)`；异步使用对应 `Async` 类，返回 Future 中的消息 ID

卡片的 `type` 写为 `tuwen`。图文卡片、[Ark](ark.md) 和 [Embed](embed.md) 是不同的消息结构，使用各自的发送重载

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### Card

```java
public static Card tuWen(String title, String description, String picUrl, String jumpUrl);
public Type getType();
public Object toObject();
```

### TuWen

```java
public TuWen(String title, String description, String picUrl, String jumpUrl);
public Card.Type getType();
public Object toObject();
```
