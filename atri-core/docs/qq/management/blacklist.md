# 群成员黑名单

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.management.GroupMemberBlacklist`

这里操作平台的群成员黑名单，与运行服务的机器人用户封禁、群黑名单配置分别管理

## 添加与移除

```java
import top.yzljc.atricore.chat.official.management.GroupMemberBlacklist;

boolean added = GroupMemberBlacklist.addMember(groupOpenId, memberOpenId);
boolean removed = GroupMemberBlacklist.removeMember(groupOpenId, memberOpenId);
```

添加黑名单不会替代踢出成员；需要同时移除成员时使用 [GroupMember](members.md) 的 `addToMemberBlacklist` 参数

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `getMemberBlacklist(groupOpenId, cursor, limit)` | `BlacklistListResult` / null | 首次 cursor 为 null，limit 默认 20、最多 100 |
| `addMember(groupOpenId, memberOpenId)` | boolean | 添加一个成员 |
| `removeMember(groupOpenId, memberOpenId)` | boolean | 移除一个成员 |
| `setMemberBlacklist(groupOpenId, op, memberOpenIds)` | boolean | 自动按 20 人分批 |
| `setMemberBlacklistDetailed(groupOpenId, op, memberOpenIds)` | `BlacklistOperationResult` / null | 单批最多 20 人 |

`op` 为 `Op.ADD` 或 `Op.DEL`，成员列表不得为空；`groupOpenId` 和成员标识均使用 OpenID

## 结果处理

`BlacklistListResult.users()` 返回当前页，`nextCursor()` 为空表示结束。每个 `BlacklistUser` 包含 `unionOpenId`、`memberOpenId`、`username`、`bannedAt` 和 `bot`

详细操作结果的 `failOpenIds()` 列出失败成员；返回 null 表示请求或解析失败。批量 boolean 方法只有每批结果均存在且失败列表为空才返回 true，已成功的操作不会回滚

```java
import java.util.List;

var result = GroupMemberBlacklist.setMemberBlacklistDetailed(
        groupOpenId, GroupMemberBlacklist.Op.ADD, List.of(memberOpenId));
if (result == null) throw new IllegalStateException("黑名单操作失败");
for (String failedMember : result.failOpenIds()) {
    System.out.println("添加失败: " + failedMember);
}
```

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### GroupMemberBlacklist

```java
public static BlacklistListResult getMemberBlacklist(String groupOpenId, String cursor, int limit);
public static boolean addMember(String groupOpenId, String memberOpenId);
public static boolean removeMember(String groupOpenId, String memberOpenId);
public static boolean setMemberBlacklist(String groupOpenId, Op op, List<String> memberOpenIds);
public static BlacklistOperationResult setMemberBlacklistDetailed(String groupOpenId, Op op, List<String> memberOpenIds);
public record BlacklistListResult(List<BlacklistUser> users, String nextCursor);
public record BlacklistUser(String unionOpenId, String memberOpenId, String username, String bannedAt, boolean bot);
public record BlacklistOperationResult(List<String> failOpenIds);
```
