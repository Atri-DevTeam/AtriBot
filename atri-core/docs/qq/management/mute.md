# 群禁言

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.management.Mute`

## 禁言与解除

```java
import top.yzljc.atricore.chat.official.management.Mute;
import java.time.Duration;

boolean muted = Mute.muteMember(groupOpenId, memberOpenId, Duration.ofMinutes(10));
boolean updated = Mute.updateMuteMember(groupOpenId, memberOpenId, Duration.ofMinutes(30));
boolean unmuted = Mute.unmuteMember(groupOpenId, memberOpenId);
```

| 方法 | 参数与行为 | 返回 |
| --- | --- | --- |
| `muteMember(groupOpenId, memberOpenId, duration)` | 新增禁言；duration 为 null 时不提交到期时间 | boolean |
| `updateMuteMember(groupOpenId, memberOpenId, duration)` | 修改禁言到期时间；duration 不能为 null | boolean |
| `unmuteMember(groupOpenId, memberOpenId)` | 解除指定成员的禁言 | boolean |
| `setGroupMemberMute(groupOpenId, members)` | 提交多个新增、修改或删除操作 | boolean |
| `queryMuteState(groupOpenId)` | 查询全员规则和成员禁言列表 | `MuteStateResult` / null |

`groupOpenId` 是群 OpenID，`memberOpenId` 是目标群成员 OpenID。时长从调用时刻起算，转换为上海时区的 RFC 3339 到期时间，精度为秒；需要永久或默认期限时应依据平台权限确认省略到期时间的语义

## 批量操作

```java
import java.util.List;

boolean accepted = Mute.setGroupMemberMute(groupOpenId, List.of(
        new Mute.MuteItem(Mute.Op.ADD, memberOpenId, "2027-01-01T10:30:00+08:00"),
        new Mute.MuteItem(Mute.Op.DEL, anotherMemberOpenId)));
```

| `MuteItem` 字段 | 说明 |
| --- | --- |
| `op` | `ADD` 新增、`UPDATE` 修改、`DEL` 删除；`value()` 返回平台字符串 |
| `memberOpenId` | 成员标识 |
| `muteExpireAt` | RFC 3339 到期时间，删除时可省略 |

::: caution 批量操作的结果
当前封装每批最多提交 10 项，超过后顺序分批。返回值根据各批 HTTP 状态汇总，没有逐成员成功结果；中途失败不回滚已成功的批次，后续批次仍会继续
:::

## 查询结果

| 结构 | 字段与含义 |
| --- | --- |
| `MuteStateResult` | `globalRule` 全员规则，可为 null；`members` 成员禁言列表 |
| `GlobalMuteRule` | `mode` 平台规则模式、`scheduleRules` 单次规则、`recurringRules` 周期规则 |
| `MuteScheduleRule` | `taskId`、`startAt`、`endAt`、`enabled` |
| `MuteRecurringRule` | `taskId`、`weekdays`、`startTime`、`endTime`、`enabled`，保持平台返回值 |
| `MemberMuteState` | `memberOpenId`、`muteExpireAt`、`username`、`unionOpenId` |

当前封装支持读取全员禁言规则，未提供修改全员规则的方法。机器人需具备平台开放的接口权限和对应群管理权限

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### Mute

```java
public record MuteItem(Op op, String memberOpenId, String muteExpireAt);
public static boolean muteMember(String groupOpenId, String memberOpenId, Duration duration);
public static boolean updateMuteMember(String groupOpenId, String memberOpenId, Duration duration);
public static boolean unmuteMember(String groupOpenId, String memberOpenId);
public static boolean setGroupMemberMute(String groupOpenId, List<MuteItem> members);
public static MuteStateResult queryMuteState(String groupOpenId);
public record MuteStateResult(GlobalMuteRule globalRule, List<MemberMuteState> members);
public record GlobalMuteRule(String mode, List<MuteScheduleRule> scheduleRules, List<MuteRecurringRule> recurringRules);
public record MuteScheduleRule(String taskId, String startAt, String endAt, boolean enabled);
public record MuteRecurringRule(String taskId, List<Integer> weekdays, String startTime, String endTime, boolean enabled);
public record MemberMuteState(String memberOpenId, String muteExpireAt, String username, String unionOpenId);
```
