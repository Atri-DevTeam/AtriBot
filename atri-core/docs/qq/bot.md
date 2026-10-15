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

一次查询分别访问机器人群内状态和群资料接口，各自经过限流等待；调用是同步的，遍历大量群时应使用业务调度任务，不在消息处理线程连续阻塞查询

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
