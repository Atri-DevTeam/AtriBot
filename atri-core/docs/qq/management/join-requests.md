# 入群申请

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.management.JoinRequestApproval`

## 处理申请

```java
import top.yzljc.atricore.chat.official.management.JoinRequestApproval;

boolean approved = JoinRequestApproval.approveJoinRequest(
        groupOpenId, memberOpenId, joinRequestId);
```

| 方法 | 说明 | 返回 |
| --- | --- | --- |
| `getJoinRequestList(groupOpenId, cursor, limit)` | 分页读取申请，首次 cursor 为 null，limit 默认 20、最多 100 | `JoinRequestListResult` / null |
| `approveJoinRequest(groupOpenId, memberOpenId, joinRequestId)` | 同意指定申请 | boolean |
| `declineJoinRequest(groupOpenId, memberOpenId, joinRequestId)` | 拒绝申请，不添加黑名单 | boolean |
| `declineJoinRequest(groupOpenId, memberOpenId, joinRequestId, rejectReason, addToMemberBlacklist)` | 拒绝并可附带原因及黑名单操作 | boolean |

群、申请人和申请 ID 均不得为空。`joinRequestId` 来自申请列表或申请事件，不能使用消息 ID；空 `rejectReason` 不提交到平台

## 列表字段

`JoinRequestListResult.list()` 为当前页，`nextCursor()` 为空表示结束，查询失败返回 null

| `JoinRequestData` 字段 | 说明 |
| --- | --- |
| `joinRequestId` | 申请 ID |
| `riskTips` | 平台风险提示 |
| `unionOpenId` / `memberOpenId` | 统一标识与成员标识，处理申请使用后者 |
| `username` / `bot` | 昵称和机器人标记 |
| `applyAt` / `applySource` | 申请时间与来源 |
| `invitedBy` | 邀请人信息，保持平台返回字符串 |
| `verifyInfo` | 验证方式与回答，可能为空 |

`VerifyInfoData` 包含 `method`、`verifyMessage` 和 `reviewQaList`；每个 `ReviewQa` 包含 `question` 和 `answer`

applySource 的已知值为 self_apply（主动申请）和 invited（邀请）；验证 method 为 verify_message（验证消息）或 admin_review_qa（管理员问答），业务应保留对未知值的处理

事件监听可使用 `OfficialGroupJoinRequestEvent`，注册方式和事件字段见 [事件](../../events.md)。审批返回 true 表示本次 HTTP 操作成功，重复审批、权限不足等情况由平台拒绝

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### JoinRequestApproval

```java
public static boolean approveJoinRequest(String groupOpenId, String memberOpenId, String joinRequestId);
public static boolean declineJoinRequest(String groupOpenId, String memberOpenId, String joinRequestId);
public static boolean declineJoinRequest(String groupOpenId, String memberOpenId, String joinRequestId, String rejectReason, boolean addToMemberBlacklist);
public static JoinRequestListResult getJoinRequestList(String groupOpenId, String cursor, int limit);
public record JoinRequestListResult(List<JoinRequestData> list, String nextCursor);
public record JoinRequestData(String joinRequestId, String riskTips, String unionOpenId, String memberOpenId, String username, String applyAt, String applySource, String invitedBy, boolean bot, VerifyInfoData verifyInfo);
public record VerifyInfoData(String method, String verifyMessage, List<ReviewQa> reviewQaList);
public record ReviewQa(String question, String answer);
```
