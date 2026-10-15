# Napcat 消息

[API 目录](../README.md) · 包 `top.yzljc.atricore.chat.napcat`

群聊使用 GroupMessage，私聊使用 PrivateMessage；群和用户标识均为 QQ 号码字符串，不使用官方机器人的 OpenID。这些方法同步等待 Napcat HTTP 响应

## 发送与回复

```java
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.chat.napcat.PrivateMessage;
import top.yzljc.atricore.chat.ImageComponent;

String groupMessageId = GroupMessage.chatMessage(groupId, "通知内容");
String replyId = GroupMessage.replyMessage(groupId, messageId, "处理完成");
String privateMessageId = PrivateMessage.chatMessage(userId,
        ImageComponent.imageOf(imageUrl));
```

| 方法族 | 参数与内容 |
| --- | --- |
| `GroupMessage.chatMessage(groupId, content)` | String、List<MessageSegment> 或 ImageComponent |
| `GroupMessage.chatMessage(userId, groupId, content, whetherAt)` | 是否在内容前提及指定用户 |
| `GroupMessage.replyMessage(groupId, messageId, content)` | 文本、Collection<MessageSegment> 或图片 |
| `GroupMessage.replyMessage(userId, groupId, messageId, whetherAt, content)` | 引用并选择是否提及 |
| `PrivateMessage.chatMessage(userId, content)` | 文本、Collection<MessageSegment> 或图片 |
| `PrivateMessage.replyMessage(userId, messageId, content)` | 引用私聊消息 |
| `GroupMessage.atUser(userId, groupId, text)` | 提及并发送文本，无返回结果 |

发送返回消息 ID，空内容、无效图片或请求失败通常返回 null。ImageComponent 的 URL 和 BASE64 均有适配，setText 设置的附加文字作为文本段发送

非阻塞调用可在 Provider 的 Scheduler 或共享执行器中执行，详见 [任务调度](../scheduling.md)，不要为每条消息创建线程池

## 撤回与转发

`GroupMessage.recallMessage(messageId)` 和 `PrivateMessage.recallMessage(messageId)` 返回 boolean，只有响应 status 为 ok 且整数 retcode 为 0 才确认成功

`GroupMessage.forwardTo(groupId, messageId)` 转发单条消息，正常返回新消息 ID；当前实现失败时可能返回原 messageId，不能仅凭非空认定成功。合并转发见 [转发消息](forwarding.md)

消息段结构见 [消息段](segments.md)，群请求、表情回应及戳一戳见 [群操作](groups.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### GroupMessage

```java
public static String chatMessage(String groupId, String text);
public static String chatMessage(String userId, String groupId, String text, boolean whetherAt);
public static String chatMessage(String groupId, List<MessageSegment> data);
public static String chatMessage(String userId, String groupId, List<MessageSegment> data, boolean whetherAt);
public static String chatMessage(String groupId, ImageComponent image);
public static String chatMessage(String userId, String groupId, ImageComponent image, boolean whetherAt);
public static String replyMessage(String groupId, String messageId, String text);
public static String replyMessage(String groupId, String messageId, Collection<MessageSegment> messageSegment);
public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, String text);
public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, Collection<MessageSegment> messageSegment);
public static String replyMessage(String groupId, String messageId, ImageComponent image);
public static String replyMessage(String userId, String groupId, String messageId, boolean whetherAt, ImageComponent image);
public static boolean recallMessage(String messageId);
public static String forwardTo(String groupId, String messageId);
public static String forwardMessage(String groupId, Collection<MessageSegment> nodes, String title, String summary, String... textVars);
public static void atUser(String userId, String groupId, String text);
public static MessageSegment createTextNode(String text);
public static MessageSegment createTextNode(String text, String uin, String name);
public static MessageSegment createTextNode(MessageSegment text);
public static MessageSegment createTextNode(MessageSegment text, String uin, String name);
public static MessageSegment createTextNode(LinkedList<MessageSegment> text);
public static MessageSegment createTextNode(LinkedList<MessageSegment> text, String uin, String name);
public static MessageSegment createImageNode(String url);
public static MessageSegment createImageNode(String url, String uin, String name);
public static MessageSegment createVideoNode(String url);
public static MessageSegment createVideoNode(String url, String uin, String name);
public static void handleRequest(boolean approve, String flag, String reason);
public static void setEmoji(String messageId, int emojiId, boolean set);
```

### PrivateMessage

```java
public static String chatMessage(String userId, Collection<MessageSegment> data);
public static String chatMessage(String userId, String text);
public static String chatMessage(String userId, ImageComponent image);
public static String replyMessage(String userId, String messageId, String text);
public static String replyMessage(String userId, String messageId, ImageComponent image);
public static String replyMessage(String userId, String messageId, Collection<MessageSegment> messageSegments);
public static boolean recallMessage(String messageId);
public static String forwardMessage(String userId, Collection<MessageSegment> nodes, String title, String summary, String... textVars);
public static MessageSegment createTextNode(String text);
public static MessageSegment createTextNode(String text, String uin, String name);
public static MessageSegment createTextNode(MessageSegment text);
public static MessageSegment createTextNode(LinkedList<MessageSegment> text);
public static MessageSegment createImageNode(String url);
public static MessageSegment createImageNode(String url, String uin, String name);
```
