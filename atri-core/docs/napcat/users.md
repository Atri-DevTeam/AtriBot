# Napcat 用户与好友

[API 目录](../README.md) · UserInformation / FriendList

## 用户查询

| 方法 | 返回与行为 |
| --- | --- |
| `getBotId()` | 当前登录 QQ 号码，首次请求后缓存，失败可为 null |
| `getUserName(userId)` | 查询并缓存昵称，失败可为 null |
| `isGroupAdmin(groupId, userId)` | 判断 owner/admin 并缓存结果 |
| `isBot(userId)` | 按平台提供的机器人号码区间判断，不是“是否为自己” |
| `getUserNtUid(userId)` | 查询 NT UID 或特殊错误字符串 |
| `clearCache()` | 当前实现只清空登录机器人 ID，不清空昵称和群角色缓存 |

上述方法均为 UserInformation 的静态方法。昵称、角色与号码区间缓存没有在该类中设置定时过期机制，不应将缓存角色作为实时远端权限保证

getUserNtUid 的 `-1` 表示输入长度无效，`-2` 表示请求无结果，`-3` 表示 UID 被星号遮盖；当前实现假设成功响应含 uid，缺失时可能抛出异常

## 好友列表

```java
import top.yzljc.atricore.chat.napcat.FriendList;

FriendList.updateFriendList();
boolean friend = FriendList.isFriend(userId);
String nickname = FriendList.getFriendNickname(userId);
int count = FriendList.getFriendCount();
```

FriendList 类首次加载会查询好友列表，后续查询读取内存缓存。updateFriendList 成功获取数组后替换缓存，失败保留已有数据；好友不存在时昵称为 null

## 好友申请

```java
import top.yzljc.atricore.platform.napcat.PostRequest;
import top.yzljc.atricore.platform.napcat.RequestType;
import java.util.Map;

var result = PostRequest.getPostResult(RequestType.ACCEPT_FRIEND_REQUEST,
        Map.of("flag", flag, "approve", true, "remark", ""));
```

flag 来自 NapcatFriendRequestEvent。事件 sendMessage 只发送私聊，不会自动同意申请；HTTP 结果判断见 [请求说明](requests.md)

## 名片与资料设置

| RequestType | 参数 | 说明 |
| --- | --- | --- |
| SEND_LIKE | user_id、times | 名片点赞 |
| SET_PROFILE | nickname、personal_note、sex | 设置机器人资料，当前业务使用这些字段 |

这些操作通过 PostRequest 调用，没有单独的类型化业务方法。用户和群标识始终使用 QQ 号码

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### UserInformation

```java
public static String getBotId();
public static String getUserName(String userId);
public static boolean isGroupAdmin(String groupId, String userId);
public static void clearCache();
public static boolean isBot(String userId);
public static String getUserNtUid(String userId);
public record BotUinInterval(long min, long max);
```

### FriendList

```java
public static boolean isFriend(String userId);
public static String getFriendNickname(String userId);
public static int getFriendCount();
public static void updateFriendList();
```
