# 入群审核策略

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.management.JoinApprovalStrategy`

策略管理与 [逐条审批](join-requests.md) 分开使用，策略保存、关联群、白名单更新及执行均有独立入口

## 创建策略

```java
import top.yzljc.atricore.chat.official.management.JoinApprovalStrategy;
import java.util.List;

String strategyId = JoinApprovalStrategy.createStrategy(
        List.of(groupOpenId), null, true, null, "成员白名单");
if (strategyId == null) throw new IllegalStateException("创建策略失败");
boolean saved = JoinApprovalStrategy.updateWhitelist(strategyId,
        JoinApprovalStrategy.WhitelistOp.ADD, List.of("123456789"));
```

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `listStrategies(cursor, limit)` | `StrategyListResult` / null | 首次 cursor 为 null，limit 默认 20、最多 100 |
| `createStrategy(groupOpenIds, groupIds, enable, expireAt, remark)` | 策略 ID / null | 创建策略 |
| `updateStrategy(strategyId, enable, expireAt, remark)` | boolean | 修改启用状态、过期时间和备注 |
| `updateStrategyGroups(strategyId, op, groupOpenIds, groupIds)` | boolean | 添加或移除关联群 |
| `updateWhitelist(strategyId, op, whitelistUsers)` | boolean | 添加或移除白名单用户 |
| `executeStrategy(strategyId)` | boolean | 提交执行请求，不等待远端任务完成 |
| `deleteStrategy(strategyId)` | boolean | 删除策略 |

## 参数约定

| 参数 | 含义 |
| --- | --- |
| `groupOpenIds` | 群 OpenID 列表 |
| `groupIds` | 群号码列表，与 groupOpenIds 二选一 |
| `enable` | 创建时 boolean；更新时 Boolean，null 表示不提交此字段 |
| `expireAt` | RFC 3339 过期时间，null 或空串不提交 |
| `remark` | 备注，null 或空串不提交 |
| `GroupActionOp.ADD` / `DEL` | 添加或移除策略关联群 |
| `WhitelistOp.ADD` / `DEL` | 添加或移除白名单用户 |
| `whitelistUsers` | **QQ 号码**列表，不是用户 OpenID |

创建时必须且只能提供一种群标识列表，更新关联群时也应保持同一种标识。当前更新方法省略空时间及空备注，不能通过空串表达清空字段

## 查询结果

`StrategyListResult.strategies()` 返回当前页，`nextCursor()` 为空表示结束

`StrategyData` 包含 `strategyId`、`groupOpenIds`、`groupIds`、`whitelistUserCount`、`enable`、`expireAt`、`createdAt`、`updatedAt`、`remark`。列表仅返回白名单人数，不包含完整白名单

这些方法同步等待本次 HTTP 响应，平台执行策略的任务生命周期不由 boolean 返回值表示

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### JoinApprovalStrategy

```java
public static StrategyListResult listStrategies(String cursor, int limit);
public static String createStrategy(List<String> groupOpenIds, List<String> groupIds, boolean enable, String expireAt, String remark);
public static boolean updateStrategy(String strategyId, Boolean enable, String expireAt, String remark);
public static boolean updateStrategyGroups(String strategyId, GroupActionOp op, List<String> groupOpenIds, List<String> groupIds);
public static boolean deleteStrategy(String strategyId);
public static boolean executeStrategy(String strategyId);
public static boolean updateWhitelist(String strategyId, WhitelistOp op, List<String> whitelistUsers);
public record StrategyListResult(List<StrategyData> strategies, String nextCursor);
public record StrategyData(String strategyId, List<String> groupOpenIds, List<String> groupIds, int whitelistUserCount, boolean enable, String expireAt, String createdAt, String updatedAt, String remark);
```
