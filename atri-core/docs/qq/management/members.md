# 群成员

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.management.GroupMember`

## 查询成员

```java
import top.yzljc.atricore.chat.official.management.GroupMember;

var member = GroupMember.getMemberInfo(groupOpenId, memberOpenId);
if (member != null) {
    System.out.println(member.username() + " " + member.memberRole());
}
```

| 方法 | 返回 | 参数 |
| --- | --- | --- |
| `getMemberList(groupOpenId, cursor)` | `MemberListResult` / null | 首次游标传 null |
| `getMemberInfo(groupOpenId, memberOpenId)` | `MemberInfo` / null | 群和成员 OpenID |
| `removeMember(groupOpenId, memberOpenId, addToMemberBlacklist)` | boolean | 移除一个成员，可同时加入黑名单 |
| `removeMembers(groupOpenId, memberOpenIds, addToMemberBlacklist)` | boolean | 自动按 20 人分批 |
| `removeMembersDetailed(groupOpenId, memberOpenIds, addToMemberBlacklist)` | `RemoveMembersResult` / null | 单批最多 20 人，返回详细结果 |

列表的 `members()` 是当前页成员，使用 `nextCursor()` 查询下一页，空游标表示结束。请求失败返回 null，此时不能将结果视为空群

## 成员字段

| `MemberInfo` 字段 | 说明 |
| --- | --- |
| `memberOpenId` | 当前群成员 OpenID，用于本页成员操作 |
| `username` | 平台返回的昵称 |
| `memberRole` | `member`、`admin` 或 `owner` |
| `bot` | 平台机器人标记 |
| `joinedAt` | 入群时间字符串 |
| `unionOpenId` | 平台提供的统一标识，可能为空，不替代 memberOpenId |

## 移除及部分失败

::: important 使用前提
群踢人需要开放平台和群主同时授权才可使用，单人移除和批量移除均适用
:::

```java
import java.util.List;

var result = GroupMember.removeMembersDetailed(groupOpenId, List.of(memberOpenId), true);
if (result == null) {
    throw new IllegalStateException("成员移除请求失败");
}
boolean success = result.success();
String removalStatus = result.removeMembersResult();
List<String> blacklistFailures = result.addToMemberBlacklistFailOpenIds();
```

::: warning 成员移除与黑名单结果
`success()` 要求移除结果为 `success`，且加入黑名单的失败列表为空。成员已移除但黑名单添加失败时仍返回 false，不能据此认定成员仍在群中
:::

批量方法不会回滚已成功批次；需要展示失败详情时自行按 20 人切分并调用详细方法

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### GroupMember

```java
public static MemberListResult getMemberList(String groupOpenId, String cursor);
public static MemberInfo getMemberInfo(String groupOpenId, String memberOpenId);
public static boolean removeMember(String groupOpenId, String memberOpenId, boolean addToMemberBlacklist);
public static boolean removeMembers(String groupOpenId, List<String> memberOpenIds, boolean addToMemberBlacklist);
public static RemoveMembersResult removeMembersDetailed(String groupOpenId, List<String> memberOpenIds, boolean addToMemberBlacklist);
public record MemberListResult(List<MemberInfo> members, String nextCursor);
public record MemberInfo(String memberOpenId, String username, String memberRole, boolean bot, String joinedAt, String unionOpenId);
public record RemoveMembersResult(String removeMembersResult, List<String> addToMemberBlacklistFailOpenIds);
```
