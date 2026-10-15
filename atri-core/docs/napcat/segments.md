# Napcat 消息段

[API 目录](../README.md) · 包 `top.yzljc.atricore.chat.napcat.impl`

`MessageSegment` 是 record，字段为 String type 和 Map<String, Object> data，不限制消息段种类。构造后通过 type()、data() 读取

```java
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.chat.napcat.impl.MessageSegment;
import java.util.List;
import java.util.Map;

var segments = List.of(
        new MessageSegment("at", Map.of("qq", userId)),
        new MessageSegment("text", Map.of("text", " 查询结果")),
        new MessageSegment("image", Map.of("file", imageUrl)));
String sentId = GroupMessage.chatMessage(groupId, segments);
```

## 当前封装使用的消息段

| type | data 字段 | 用途 |
| --- | --- | --- |
| text | text | 文本 |
| at | qq | 发送时提及 QQ 用户 |
| reply | id | 发送时引用消息 ID |
| image | file 或 url | 图片；Base64 使用 file，值以 base64:// 开头 |
| video | file | 视频 |
| node | uin、name、content | 合并转发的作者和内容 |

使用 MessageSegment 可传递 Napcat 支持的扩展段，具体字段由对应 Napcat 版本解释，core 不自动进行平台消息段校验

## 解析原始 CQ 文本

`StructRawMessage.parse(rawMessage)` 返回 LinkedList<MessageSegment>，null 或空输入返回空列表。解析器保留普通文本并识别 CQ 段

::: warning 解析结果与发送结构
其结果用于内部结构化读取，会做以下归一化：图片和视频保留 url，at 的 qq 改为 user_id，reply 的 id 改为 message_id。因此解析结果不能直接视为无损的发送消息段，重新发送时应转换相应字段
:::

## 底层组合方法

MessageUtils 提供 GroupMessage、PrivateMessage 使用的底层组合操作，包括发送、引用、转发、节点构造、请求处理和撤回。业务调用优先使用按场景划分的聊天类；需要 Collection 输入或底层段组合时可查阅下方签名

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### MessageUtils

```java
public static String groupMessage(String groupId, Collection<MessageSegment> data);
public static String privateMessage(String userId, Collection<MessageSegment> data);
public static String privateMessage(String userId, String text);
public static String privateMessage(String userId, ImageComponent image);
public static String replyPrivateMessage(String userId, String messageId, String text);
public static String replyPrivateMessage(String userId, String messageId, ImageComponent image);
public static String replyPrivateMessage(String userId, String messageId, Collection<MessageSegment> messageSegments);
public static String chatMessage(String userId, String groupId, String text, boolean whetherAt);
public static String chatMessage(String userId, String groupId, Collection<MessageSegment> data, boolean whetherAt);
public static String chatMessage(String userId, String groupId, ImageComponent image, boolean whetherAt);
public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, String text);
public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, Collection<MessageSegment> messageSegments);
public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, ImageComponent image);
public static String sendPrivateForwardMessage(String userId, Collection<MessageSegment> nodes, String title, String summary, String... textVars);
public static String sendGroupForwardMessage(String groupId, Collection<MessageSegment> nodes, String title, String summary, String... textVars);
public static String forwardSingleGroupMsg(String groupId, String messageId);
public static String sendSingleImageGroupMessage(String groupId, ImageComponent image);
public static void atUser(String userId, String groupId, String text);
public static MessageSegment createTextNodeSegment(String text);
public static MessageSegment createTextNodeSegment(String text, String uin, String name);
public static MessageSegment createTextNodeSegment(MessageSegment rawMessage);
public static MessageSegment createTextNodeSegment(MessageSegment rawMessage, String uid, String name);
public static MessageSegment createTextNodeSegment(LinkedList<MessageSegment> arrayMessage);
public static MessageSegment createTextNodeSegment(LinkedList<MessageSegment> arrayMessage, String uid, String name);
public static MessageSegment createImageNodeSegment(String url);
public static MessageSegment createImageNodeSegment(String url, String uin, String name);
public static MessageSegment createVideoNodeSegment(String url);
public static MessageSegment createVideoNodeSegment(String url, String uin, String name);
public static void handleGroupRequest(boolean approve, String flag, String reason);
public static boolean recallMessage(String messageId);
public static void setEmoji(String msgId, int emojiId, boolean set);
```

### StructRawMessage

```java
public static LinkedList<MessageSegment> parse(String rawMessage);
```
