# Napcat 群资料与群操作

[API 目录](../README.md) · 包 `top.yzljc.atricore.chat.napcat`

## 群资料

| 方法 | 返回与行为 |
| --- | --- |
| `GroupInformation.getGroupName(groupId)` | 查询群名称；HTTP 请求没有结果时回退群号码 |
| `GroupInformation.fetchAllGroupIds()` | 查询群号码集合；开启 debugMode 时仅返回配置的调试群 |
| `UserInformation.isGroupAdmin(groupId, userId)` | 群主或管理员为 true，带缓存 |

群号参数均为 String。fetchAllGroupIds 的空结果可能来自失败，不宜单凭空集合认定机器人已退出全部群

## 申请与邀请

`GroupMessage.handleRequest(approve, flag, reason)` 处理申请，flag 来自事件，reason 可为 null。返回 void，不提供平台成功确认；该辅助方法固定提交 sub_type=add

::: important 区分申请与邀请
邀请事件的 sub_type=invite 不适合直接复用该辅助方法，应使用原始请求并保留事件的 sub_type
:::

```java
import top.yzljc.atricore.platform.napcat.PostRequest;
import top.yzljc.atricore.platform.napcat.RequestType;
import java.util.Map;

var result = PostRequest.getPostResult(RequestType.HANDLE_GROUP_PENDING_REQUEST,
        Map.of("flag", flag, "sub_type", subType, "approve", true));
boolean success = result != null && "ok".equals(result.path("status").asText())
        && result.path("retcode").asInt(-1) == 0;
```

`NapcatGroupRequestEvent.reject()` 和 `reject(reason)` 使用上述固定 add 的辅助方法，事件字段见 [事件](../events.md)

## 戳一戳与表情回应

| 方法 | 参数 |
| --- | --- |
| `SendPoke.poke(targetId, groupId)` | 目标 QQ 号码、群号码，目标为配置的机器人自身时不发送 |
| `GroupMessage.setEmoji(messageId, emojiId, set)` | 消息 ID、表情编号、添加或取消 |

这两个入口返回 void，调用结束不表示已得到业务成功确认。需要确认结果时使用 PostRequest 的带结果方法

## 群签到、退出与移除成员

| RequestType | 路径 | 当前接入方式 |
| --- | --- | --- |
| SEND_SIGN | /send_group_sign | PostRequest，参数 group_id |
| QUIT_GROUP | /set_group_leave | PostRequest，参数 group_id、is_dismiss |
| SET_GROUP_KICK_MEMBER | /set_group_kick_members | 仅登记请求路径，尚无类型化成员移除方法 |

当前 Napcat 封装没有与 QQ 官方 Mute 对应的禁言业务类，不应把官方 OpenID 禁言接口用于 Napcat 群。原始请求说明见 [HTTP 调用](requests.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### GroupInformation

```java
public static String getGroupName(String groupId);
public static Set<String> fetchAllGroupIds();
```

### SendPoke

```java
public static void poke(String targetId, String groupId);
```
