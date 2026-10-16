# Napcat HTTP 调用

[API 目录](../README.md) · `top.yzljc.atricore.platform.napcat`

PostRequest 从 Provider 设置读取 Napcat 地址和鉴权 Token，使用 JSON POST 调用 RequestType 中登记的路径

## 调用与返回

```java
import top.yzljc.atricore.platform.napcat.PostRequest;
import top.yzljc.atricore.platform.napcat.RequestType;
import java.time.Duration;
import java.util.Map;

var result = PostRequest.getPostResult(RequestType.GET_GROUP_MEMBER_INFO,
        Map.of("group_id", groupId, "user_id", userId), Duration.ofSeconds(10));
if (result == null) throw new IllegalStateException("HTTP 请求失败");
if (!"ok".equals(result.path("status").asText()) || result.path("retcode").asInt(-1) != 0) {
    throw new IllegalStateException("Napcat 操作失败: " + result);
}
var data = result.path("data");
```

| 方法 | 返回 | 用途 |
| --- | --- | --- |
| `getPostResult(type)` | JsonNode / null | 无参数 |
| `getPostResult(type, params)` | JsonNode / null | 多参数 Map |
| `getPostResult(type, params, timeout)` | JsonNode / null | 自定义请求超时 |
| `getSimplePostResult(type, key, value)` | JsonNode / null | 单参数 |
| `sendPost(type, params)` | void | 请求但忽略响应 |
| `sendSimplePost(type, key, value)` | void | 单参数且忽略响应 |

方法同步等待结果，HTTP 非 2xx、空响应或请求异常返回 null，线程中断会恢复标记

::: important 检查业务结果
HTTP 2xx 的 JSON 不会自动检查 `status` / `retcode`，调用方必须检查业务结果，不能仅凭响应非 null 判断操作成功
:::

## 已登记的请求

| 分类 | RequestType | 路径 |
| --- | --- | --- |
| 用户 | GET_USER_INFO / GET_STRANGER_INFO | /get_stranger_info |
| 用户 | GET_LOGIN_INFO | /get_login_info |
| 用户 | BOT_UIN_RANGE | /get_robot_uin_range |
| 好友 | GET_FRIEND_LIST | /get_friend_list |
| 好友 | ACCEPT_FRIEND_REQUEST | /set_friend_add_request |
| 资料 | SEND_LIKE | /send_like |
| 资料 | SET_PROFILE | /set_qq_profile |
| 群资料 | GET_GROUP_INFO | /get_group_info |
| 群资料 | GET_GROUP_LIST | /get_group_list |
| 群资料 | GET_GROUP_MEMBER_INFO | /get_group_member_info |
| 群操作 | SEND_SIGN | /send_group_sign |
| 群操作 | GROUP_POKE | /group_poke |
| 群操作 | HANDLE_GROUP_PENDING_REQUEST | /set_group_add_request |
| 群操作 | QUIT_GROUP | /set_group_leave |
| 群操作 | SET_GROUP_KICK_MEMBER | /set_group_kick_members |
| 消息 | SEND_GROUP_MSG | /send_group_msg |
| 消息 | SEND_PRIVATE_MSG | /send_private_msg |
| 消息 | RECALL_MESSAGE | /delete_msg |
| 转发 | SEND_GROUP_FORWARD_MSG | /send_forward_msg |
| 转发 | SEND_PRIVATE_FORWARD_MSG | /send_private_forward_msg |
| 转发 | FORWARD_SINGLE_MSG | /forward_group_single_msg |
| 交互 | PUT_EMOJI | /set_msg_emoji_like |
| 交互 | CLICK_INLINE_KEYBOARD_BUTTON | /click_inline_keyboard_button |
| 语音 | FETCH_PTT_TEXT | /fetch_ptt_text |

GET_USER_INFO 与 GET_STRANGER_INFO 是同一路径的两个枚举名，getRequestLink() 返回路径字符串

::: note 请求枚举与参数模型
列表中只有请求枚举、没有高层封装的能力，不代表 core 已提供完整参数模型。特别是批量移除成员和点击内联按钮，当前 core 未提供类型化调用或参数校验，需按实际 Napcat 服务版本组织参数
:::

群操作与资料参数见 [群操作](groups.md)、[用户与好友](users.md)；消息和转发优先使用 [聊天类](messages.md)

## 语音识别与调试群

当前业务使用 FETCH_PTT_TEXT 时传 message_id，从结果 data.text 读取文本；返回 null 或缺少该字段时不应视为识别成功

`chat.napcat.NapcatDebugGroup.sendAsync(message)` 将文本提交到共享执行器，并发送到配置的 Napcat 调试群，返回 void，不提供发送结果 Future

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### PostRequest

```java
public static JsonNode getPostResult(RequestType type, Map<String, Object> params);
public static JsonNode getPostResult(RequestType type, Map<String, Object> params, Duration timeout);
public static JsonNode getSimplePostResult(RequestType type, String key, Object value);
public static JsonNode getPostResult(RequestType type);
public static void sendPost(RequestType type, Map<String, Object> params);
public static void sendSimplePost(RequestType type, String key, Object value);
```

### NapcatDebugGroup

```java
public static void sendAsync(String message);
```
