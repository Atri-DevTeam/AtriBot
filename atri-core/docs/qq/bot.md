# 机器人与群资料

[API 目录](../README.md) · 包 `top.yzljc.atricore.platform.qq`

## 机器人资料

Provider 启动 QQ 服务时调用 `QQBot.fetchBotInfo()`，业务也可主动刷新。该方法同步访问平台，并更新静态资料字段

| 字段 | 内容 |
| --- | --- |
| BOT_UNIONID | 平台返回的机器人 union_openid，可能为空 |
| BOT_AVATAR_URL | 头像地址 |
| BOT_NAME | 名称 |
| BOT_SHARE_LINK | 分享链接 |
| BOT_WELCOME_MSG | 平台欢迎语 |
| BOT_UIN | 机器人 QQ 号码，可能为空 |

这些字段是最近一次刷新结果，不会每次读取都请求平台，也不表示业务默认加群欢迎配置

## 自定义分享链接

`top.yzljc.atricore.chat.official.ShareBot` 封装 `POST /v2/generate_url_link`，使用当前 Provider 的 QQ 接口地址与令牌

```java
import top.yzljc.atricore.chat.official.ShareBot;

String shareLink = ShareBot.generateShareLink(userOpenId);
if (shareLink != null) {
    String shortCode = ShareBot.getShareShortCode(shareLink);
}
```

| 方法 | 参数 | 返回与行为 |
| --- | --- | --- |
| `generateShareLink(callbackData)` | 业务回调标识，不得为 null，内容须符合平台 callback_data 要求 | 同步请求生成链接，失败或响应缺少有效链接时返回 null |
| `getShareShortCode(shareLink)` | 平台返回的分享链接，允许 null | 提取最后一个斜杠后的短码；输入为空、不含斜杠或以斜杠结尾时返回 null，不请求平台 |

生成方法每次调用均请求平台。链接缓存、回调标识的业务含义、邀请关系和奖励由运行服务处理；主项目使用邀请人的 OpenID 作为回调标识，在 `OfficialFriendAddEvent.getSceneParam()` 中读取后处理邀请记录

## 群资料

```java
import top.yzljc.atricore.platform.qq.QQBot;

var result = QQBot.fetchGroupProfileDetailed(groupOpenId);
if (result.profile() != null) {
    System.out.println(result.profile().groupName());
} else if (result.isDefunctGroup()) {
    System.out.println("平台返回群失效状态");
} else {
    System.err.println("群资料查询失败: " + result.errCode());
}
```

| 方法 | 返回与行为 |
| --- | --- |
| `fetchGroupProfile(groupOpenId)` | GroupProfile，失败返回 null |
| `fetchGroupProfileDetailed(groupOpenId)` | GroupProfileResult，含资料和错误码 |
| `fetchGroupProfileDetailed(groupOpenId, logOnlyOnError)` | true 时仅记录失败请求日志 |

::: tip 批量查询群资料
一次查询分别访问机器人群内状态和群资料接口，各自经过限流等待；调用是同步的，遍历大量群时应使用业务调度任务，不在消息处理线程连续阻塞查询
:::

## GroupProfile

| 字段 | 说明 |
| --- | --- |
| memberOpenId | 机器人在群内的成员标识 |
| joinTime | 机器人入群时间 |
| allowProactiveMsg | 是否允许主动消息 |
| receiveMsgSetting | ONLY_MENTION、MENTION_AND_CONTEXT 或 ALL |
| memberRole | 平台角色 |
| groupId | 群 OpenID，尽管字段名为 groupId，也不是群号码 |
| groupName | 群名称 |
| groupFingerMemo | 群简介 |
| groupClassText | 群分类 |
| groupTags | 标签列表 |
| groupMemberNum | 成员数 |

`GroupProfile.Scope.from(value)` 将平台字符串转为枚举，未知值回退 ONLY_MENTION；`getJsonValue()` 返回对应平台字符串

`GroupProfileResult.isDefunctGroup()` 按当前实现登记的失效错误码判断，普通网络失败不应据此删除群数据

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### QQBot

```java
public static void fetchBotInfo();
public static GroupProfile fetchGroupProfile(String groupId);
public static GroupProfileResult fetchGroupProfileDetailed(String groupId);
public static GroupProfileResult fetchGroupProfileDetailed(String groupId, boolean logOnlyOnError);
public record GroupProfileResult(GroupProfile profile, String errCode);
```
