# Napcat 合并转发

[API 目录](../README.md) · GroupMessage / PrivateMessage

合并转发由多个 node 消息段组成，发送到群和私聊分别调用对应聊天类

```java
import top.yzljc.atricore.chat.napcat.GroupMessage;
import java.util.List;

var nodes = List.of(
        GroupMessage.createTextNode("第一条结果", userId, "查询用户"),
        GroupMessage.createImageNode(imageUrl, userId, "查询用户"));
String sentId = GroupMessage.forwardMessage(groupId, nodes,
        "查询结果", "共两条结果", "第一条结果", "图片结果");
```

## 发送参数

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| groupId / userId | String | 群或私聊目标 |
| nodes | Collection<MessageSegment> | 转发节点列表 |
| title | String | 转发来源标题 source |
| summary | String | 摘要 |
| textVars | String... | 预览 news 文本；null 或空项被过滤 |

返回消息 ID，失败返回 null。私聊使用 `PrivateMessage.forwardMessage(userId, nodes, title, summary, textVars...)`

## 节点工厂

| 方法 | 内容 |
| --- | --- |
| `createTextNode(text)` | 单条文本 |
| `createTextNode(text, uin, name)` | 指定节点作者 |
| `createTextNode(messageSegment)` | 单个消息段 |
| `createTextNode(linkedList)` | 多消息段 |
| `createImageNode(url)` / `createImageNode(url, uin, name)` | 图片节点 |
| `GroupMessage.createVideoNode(url)` / `(url, uin, name)` | 视频节点 |

GroupMessage 另有消息段与 LinkedList 版本的指定作者重载。未指定作者的工厂使用 core 内置展示身份，不会自动使用当前消息发送者；实际业务应显式传入 uin 和 name

所有重载见 [消息方法索引](messages.md)，节点底层实现见 [消息段](segments.md)
