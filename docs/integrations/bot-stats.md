# 机器人统计接口

[文档目录](../README.md) · [WebUI 与 Miniapp](../web.md)

`GET /openapi/bot_stats` 返回 QQ 官机的在档数量、消息汇总、小游戏成绩、卡片收藏、签到与图源投稿统计

## 目录

- [请求方式](#请求方式)
- [响应格式](#响应格式)
- [在档用户与群](#在档用户与群)
- [消息统计](#消息统计)
- [统计模块与排行榜](#统计模块与排行榜)
- [小游戏公共约定](#小游戏公共约定)
- [扫雷](#扫雷)
- [听声辨物](#听声辨物)
- [四子棋](#四子棋)
- [轮盘](#轮盘)
- [猜拳](#猜拳)
- [反应力](#反应力)
- [今日钓鱼](#今日钓鱼)
- [卡片收藏与金粒](#卡片收藏与金粒)
- [签到](#签到)
- [图源投稿](#图源投稿)
- [异常与空值](#异常与空值)
- [接入示例](#接入示例)

## 请求方式

| 项目 | 说明 |
| --- | --- |
| 方法与路径 | `GET /openapi/bot_stats` |
| 鉴权 | 无需登录、Cookie 或 Token |
| WebUI 开关 | 不影响本接口 |
| 请求参数与请求体 | 均不需要 |
| 响应格式 | JSON |
| 数据范围 | QQ 官机相关统计，不返回 `platform` 字段 |
| 数据量 | 全部个人成绩及完整排行榜，无分页、条数限制或日期筛选 |
| 请求频控 | 每 IP 每分钟 60 次，全局每分钟 600 次，与现有公开工具接口共用计数 |
| 服务端缓存 | 聚合结果缓存 60 秒，过期后由下一次请求刷新 |
| HTTP 缓存 | 响应头 `Cache-Control: no-store` |
| 访问日志 | 每次请求完成后以 INFO 级别记录方法、路径、来源 IP、HTTP 状态及耗时（毫秒），包括参数错误和限流响应 |

```bash
curl 'https://bot.example.com/openapi/bot_stats'
```

域名与端口使用项目 HTTP 服务配置。旧 `/bot_stats` 及旧公开 GET 统计路由不再提供服务

接口只接受无参数请求，不提供按用户查询的入口。传入任何查询参数（包括 `userOpenId`、`platform`、`days`、`start`、`end`、`limit`）均返回 HTTP `400`，不会执行统计查询

## 响应格式

以下示例均为对应位置的响应片段，数值为示意数据。标明“节选”的示例省略了其他字段；实际响应字段以字段表为准

### 外层结构

成功响应节选：

```json
{
  "status": 200,
  "message": "ok",
  "data": {
    "schemaVersion": 1,
    "generatedAt": 1791259200000,
    "cacheSeconds": 60,
    "unavailable": []
  },
  "timestamp": 1791259201000
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `status` | integer | 业务状态，成功为 `200`，失败为 `432` |
| `message` | string | 成功为 `ok`，失败时为错误说明 |
| `data` | object 或 null | 聚合统计；请求整体失败时为 `null` |
| `timestamp` | integer | 本次响应生成时间，Unix 毫秒时间戳 |

HTTP 状态与业务状态需要分别判断，见[异常与空值](#异常与空值)

### `data` 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `schemaVersion` | integer | 响应结构版本，当前为 `1` |
| `generatedAt` | integer | 数据库聚合结果生成时间，Unix 毫秒时间戳 |
| `cacheSeconds` | integer | 服务端缓存有效期，当前为 `60` 秒 |
| `registrations` | object 或 null | 在档用户数与群数 |
| `messages` | object 或 null | 累计、今日消息汇总及平均日活 |
| `games` | object 或 null | 七种小游戏的累计统计、个人成绩及排行榜 |
| `loot` | object 或 null | 当前卡片拥有数、目录完成度及金粒余额 |
| `sign` | object 或 null | 累计与今日签到统计 |
| `images` | object 或 null | 图源投稿及审核状态统计 |
| `unavailable` | string[] | 本次无法读取的模块名称，全部可用时为 `[]` |

`timestamp` 随请求更新；缓存命中时 `generatedAt` 保持不变。各模块依次读取，并非同一数据库事务中的严格一致快照

### 类型与计量约定

| 类型或单位 | 约定 |
| --- | --- |
| `integer` | JSON 整数；累计计数可能为 64 位整数，接入端应注意数值精度 |
| `number` | JSON 数值，可包含小数；接口不固定小数位数 |
| 用户标识 | `userId` 为 QQ 官机用户 OpenID，按字符串处理，不是 QQ 号 |
| 日期 | `YYYY-MM-DD`；今日统计按 `Asia/Shanghai` 确定日期 |
| 时间戳 | Unix 毫秒时间戳 |
| 游戏耗时 | 毫秒，表示持续时间 |
| 百分比 | `0`～`100`，例如 `75.0` 表示 `75%`；分母为零时返回 `null` |

## 在档用户与群

位置：`data.registrations`

```json
{
  "users": 1250,
  "groups": 86
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `users` | integer | `official_users` 当前行数 |
| `groups` | integer | `official_groups` 当前行数 |

这是当前数据库中的在档数量，不等同于活跃人数、当前好友数或机器人仍在群内的群数。删除数据库记录后，相应数量会减少

## 消息统计

位置：`data.messages`

```json
{
  "todayDate": "2026-10-06",
  "total": {
    "groupReceived": 120000,
    "groupSent": 68000,
    "c2cReceived": 16000,
    "c2cSent": 14000,
    "activeUsers": 960,
    "groupActiveUsers": 960,
    "c2cActiveUsers": 240,
    "sentGroups": 75,
    "sentC2CUsers": 230
  },
  "today": {
    "groupReceived": 1200,
    "groupSent": 680,
    "c2cReceived": 160,
    "c2cSent": 140,
    "activeUsers": 96,
    "groupActiveUsers": 96,
    "c2cActiveUsers": 24,
    "sentGroups": 30,
    "sentC2CUsers": 23
  },
  "averageDailyDau": 85.5
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `todayDate` | string | 今日统计对应的日期 |
| `total` | object | 当前聊天统计快照覆盖范围内的累计数据 |
| `today` | object | `todayDate` 对应日期的数据，字段与 `total` 相同 |
| `averageDailyDau` | number | 有接收用户记录的各日 DAU 之和，除以这些日期的数量；没有此类日期时为 `0` |

`total` 与 `today` 的字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `groupReceived` | integer | 收到的群聊用户消息数，不含机器人消息 |
| `groupSent` | integer | 本机器人发送的群聊消息数，新增记录以 `BOT_SEND` 为依据 |
| `c2cReceived` | integer | 收到的用户私聊消息数 |
| `c2cSent` | integer | 机器人发送的私聊消息数 |
| `activeUsers` | integer | 对应范围内发送过群聊或私聊消息的用户去重数 |
| `groupActiveUsers` | integer | 当前实现与 `activeUsers` 取自同一集合，数值相同；不能作为群聊独立活跃人数使用 |
| `c2cActiveUsers` | integer | 对应范围内发送过私聊消息的用户去重数 |
| `sentGroups` | integer | 本机器人在对应范围内发送过消息的群去重数 |
| `sentC2CUsers` | integer | 本机器人在对应范围内发送过私聊消息的目标用户去重数 |

累计活跃人数按整个范围去重，不是每日活跃人数相加。`averageDailyDau` 的分母不包含无接收用户记录的日期，也不包含仅有机器人发送记录的日期

此模块读取既有聊天统计快照，历史覆盖范围取决于已经保存的数据。不返回每日切片、消息正文、逐条消息记录或各用户的消息明细

## 统计模块与排行榜

`games` 下的每个游戏，以及 `loot`、`sign`、`images`，均采用以下结构。本例节选自听声辨物：

```json
{
  "totals": {"players": 2, "correct": 10},
  "players": [
    {"userId": "USER_A", "correct": 8, "ranks": {"correct": 1}},
    {"userId": "USER_B", "correct": 2, "ranks": {"correct": 2}}
  ],
  "leaderboards": {
    "correct": [
      {"rank": 1, "userId": "USER_A", "value": 8},
      {"rank": 2, "userId": "USER_B", "value": 2}
    ]
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totals` | object | 模块的全局汇总 |
| `players` | object[] | 该模块全部符合条件的个人记录，按 `userId` 字符串升序排列 |
| `players[].userId` | string | 用户 OpenID |
| `players[].ranks` | object | 指标名到整数名次的映射 |
| `leaderboards` | object | 指标名到完整榜单数组的映射 |
| `leaderboards.<指标>[].rank` | integer | 名次，从 `1` 开始 |
| `leaderboards.<指标>[].userId` | string | 排名对应的用户 OpenID |
| `leaderboards.<指标>[].value` | number | 对应指标原始值，单位与该指标相同 |

排名规则：

1. 仅数值大于 `0` 的记录参与排名，`0`、`null` 和缺失值不入榜
2. 并列使用 `1、1、3` 规则；同值记录按 `userId` 字符串升序排列
3. `bestMs`、`averageCompletedMs`、`todayCheckInTime` 按升序排名，其余指标按降序排名
4. 无最低场次门槛。例如只完成一场且获胜的用户，胜率可为 `100%`
5. 未进入某项榜单时，个人 `ranks` 中没有该指标的键，不返回 `rank: 0` 占位
6. 没有入榜用户时，对应榜单仍存在，值为 `[]`

不是每个数字字段都有排行榜。各模块下列出的“排行榜指标”为完整清单

## 小游戏公共约定

位置：`data.games`。读取成功时固定包含 `minesweeper`、`sound`、`connect4`、`roulette`、`rsp`、`reaction`、`fishing` 七个键，每个键的值均采用上述统计模块结构

每个游戏的 `totals` 包含以下全局字段，个人记录不包含这两个字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `players` | integer | 至少一个已知累计指标大于零，或有效 `bestMs` 大于零的用户数，等于该游戏 `players` 数组长度 |
| `recordedRounds` | integer | 事件表中可识别的去重结算事件数；听声辨物为有效答题事件数 |

`recordedRounds` 按游戏内事件 ID 去重，排除操作、开局等带冒号后缀的事件及 `legacy-` 导入事件。它不是全部历史局数，也不等于参与场次之和。多人同局只计一个结算事件，但会增加多名用户的 `plays`

各游戏计数字段同时出现在 `totals` 和 `players[]`，前者为个人计数之和。比例按汇总后的分子和分母重新计算，不是个人比例的平均值。个人记录另包含 `userId` 与 `ranks`

旧扫雷 `operations` 在缺少 `digs` 时全部归入挖格；旧听声辨物的 `wrong` 按 `operations - correct` 补齐。其他新增指标从启用后累计，旧败场、平局、超时等不推算。历史数据不保证满足 `plays = wins + losses + draws` 等等式

## 扫雷

位置：`data.games.minesweeper`

`totals` 示例：

```json
{
  "players": 2,
  "recordedRounds": 5,
  "participations": 10,
  "plays": 10,
  "wins": 6,
  "losses": 4,
  "operations": 160,
  "digs": 120,
  "mineHits": 2,
  "flagsPlaced": 30,
  "flagsRemoved": 10,
  "winRate": 60.0
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `participations` | integer | 参与次数，每位用户每局首次有效操作时加一 |
| `plays` | integer | 已结算的参与场次 |
| `wins` | integer | 参与并通关的次数 |
| `losses` | integer | 参与但本局失败的次数 |
| `operations` | integer | 有效挖格、插旗、拔旗操作总数 |
| `digs` | integer | 有效挖格操作次数；一次自动展开多个格子仍只计一次 |
| `mineHits` | integer | 本人挖中地雷的次数，只记到触雷用户 |
| `flagsPlaced` | integer | 成功插旗次数 |
| `flagsRemoved` | integer | 成功拔旗次数 |
| `winRate` | number 或 null | `wins / plays × 100` |

结算时，所有有有效操作的参与者均记录通关或失败。重复挖已打开格子、挖插旗格子等无效操作不计数

排行榜指标：`participations`、`plays`、`wins`、`losses`、`operations`、`digs`、`mineHits`、`flagsPlaced`、`flagsRemoved`、`winRate`

## 听声辨物

位置：`data.games.sound`

`totals` 示例：

```json
{
  "players": 2,
  "recordedRounds": 20,
  "operations": 20,
  "correct": 16,
  "wrong": 4,
  "accuracy": 80.0
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `operations` | integer | 有效答题次数 |
| `correct` | integer | 答对次数 |
| `wrong` | integer | 答错次数 |
| `accuracy` | number 或 null | `correct / operations × 100` |

本游戏不返回 `plays`、`wins` 或 `winRate`

排行榜指标：`operations`、`correct`、`wrong`、`accuracy`

## 四子棋

位置：`data.games.connect4`

`totals` 示例：

```json
{
  "players": 2,
  "recordedRounds": 5,
  "participations": 10,
  "plays": 10,
  "wins": 4,
  "losses": 4,
  "draws": 2,
  "moves": 100,
  "normalWins": 3,
  "timeoutWins": 1,
  "timeoutPlays": 2,
  "moveTimeouts": 1,
  "winRate": 40.0
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `participations` | integer | 满员开局后的参与次数，等待阶段取消不计入 |
| `plays` | integer | 已结算的参与场次 |
| `wins` | integer | 胜场，包含正常获胜和对手超时获胜 |
| `losses` | integer | 败场 |
| `draws` | integer | 平局场次 |
| `moves` | integer | 有效落子次数 |
| `normalWins` | integer | 连成四子获胜次数 |
| `timeoutWins` | integer | 因对手超时而获胜的次数 |
| `timeoutPlays` | integer | 因超时结束的参与场次，双方各计一次 |
| `moveTimeouts` | integer | 本人落子超时次数，只计入超时的一方 |
| `winRate` | number 或 null | `wins / plays × 100`，平局计入分母 |

排行榜指标：`participations`、`plays`、`wins`、`losses`、`draws`、`moves`、`normalWins`、`timeoutWins`、`timeoutPlays`、`moveTimeouts`、`winRate`

## 轮盘

位置：`data.games.roulette`

`totals` 示例：

```json
{
  "players": 6,
  "recordedRounds": 2,
  "participations": 12,
  "plays": 12,
  "wins": 2,
  "losses": 10,
  "actions": 24,
  "selfTargets": 8,
  "otherTargets": 16,
  "timeouts": 3,
  "winRate": 16.666666666666668
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `participations` | integer | 满员开局后的参与次数 |
| `plays` | integer | 已结算的参与场次 |
| `wins` | integer | 最终幸存次数 |
| `losses` | integer | 淘汰次数 |
| `actions` | integer | 手动操作次数 |
| `selfTargets` | integer | 手动指定自己为目标的次数 |
| `otherTargets` | integer | 手动指定他人为目标的次数 |
| `timeouts` | integer | 超时后自动执行操作的次数 |
| `winRate` | number 或 null | `wins / plays × 100` |

超时自动操作不计入 `actions`、`selfTargets` 或 `otherTargets`

排行榜指标：`participations`、`plays`、`wins`、`losses`、`actions`、`selfTargets`、`otherTargets`、`timeouts`、`winRate`

## 猜拳

位置：`data.games.rsp`

`totals` 示例：

```json
{
  "players": 2,
  "recordedRounds": 5,
  "participations": 11,
  "plays": 10,
  "wins": 4,
  "losses": 4,
  "draws": 2,
  "rock": 4,
  "scissors": 3,
  "paper": 4,
  "winRate": 40.0
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `participations` | integer | 有效出拳参与次数 |
| `plays` | integer | 已结算的参与场次 |
| `wins` | integer | 胜场 |
| `losses` | integer | 败场 |
| `draws` | integer | 平局场次 |
| `rock` | integer | 出石头次数 |
| `scissors` | integer | 出剪刀次数 |
| `paper` | integer | 出布次数 |
| `winRate` | number 或 null | `wins / plays × 100`，平局计入分母 |

有效出拳后即累计拳型。后续因对手超时而取消时，保留已出拳记录，但不增加结算场次

排行榜指标：`participations`、`plays`、`wins`、`losses`、`draws`、`rock`、`scissors`、`paper`、`winRate`

## 反应力

位置：`data.games.reaction`

`totals` 示例：

```json
{
  "players": 2,
  "recordedRounds": 10,
  "plays": 10,
  "wins": 6,
  "hits": 720,
  "misses": 12,
  "abandoned": 1,
  "timeouts": 3,
  "timedCompletions": 6,
  "totalCompletedMs": 120000,
  "completionRate": 60.0,
  "averageCompletedMs": 20000.0,
  "bestMs": 18000
}
```

`players[]` 中的一条个人记录示例，`ranks` 仅节选耗时排名：

```json
{
  "userId": "USER_A",
  "plays": 5,
  "wins": 3,
  "hits": 360,
  "misses": 6,
  "abandoned": 1,
  "timeouts": 1,
  "timedCompletions": 3,
  "totalCompletedMs": 60000,
  "bestMs": 18000,
  "bestMisses": 0,
  "bestDate": "2026-10-06",
  "completionRate": 60.0,
  "averageCompletedMs": 20000.0,
  "ranks": {"bestMs": 1, "averageCompletedMs": 1}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `plays` | integer | 已结算场次，包含完成、超时和主动放弃 |
| `wins` | integer | 完成次数 |
| `hits` | integer | 正确点击次数，完成、超时或放弃时累计 |
| `misses` | integer | 失误次数，完成、超时或放弃时累计 |
| `abandoned` | integer | 主动放弃次数 |
| `timeouts` | integer | 超时次数 |
| `timedCompletions` | integer | 有完成耗时记录的场次，用于计算平均耗时 |
| `totalCompletedMs` | integer | 上述场次的耗时之和，单位毫秒 |
| `completionRate` | number 或 null | `wins / plays × 100` |
| `averageCompletedMs` | number 或 null | `totalCompletedMs / timedCompletions`，无有效样本时为 `null` |
| `bestMs` | integer；全局可为 null | 个人最佳完成耗时；全局取所有有效个人成绩最小值，单位毫秒 |
| `bestMisses` | integer | 仅个人记录包含，最佳成绩对应的失误数 |
| `bestDate` | string 或 null | 仅个人记录包含，最佳成绩日期；缺失或格式不符合 `YYYY-MM-DD` 时为 `null` |

个人没有有效最佳成绩时，`bestMs` 为 `0`，`bestMisses` 缺失时按 `0` 返回；这种 `0` 不表示完成成绩。全局没有有效成绩时，`totals.bestMs` 为 `null`

历史 `wins` 可能包含没有耗时记录的完成场次，不能用它代替 `timedCompletions` 计算平均耗时。统计归属于游戏发起者

排行榜指标：`plays`、`wins`、`hits`、`misses`、`abandoned`、`timeouts`、`timedCompletions`、`totalCompletedMs`、`completionRate`、`bestMs`、`averageCompletedMs`。其中 `bestMs`、`averageCompletedMs` 按升序，累计耗时 `totalCompletedMs` 按降序

## 今日钓鱼

位置：`data.games.fishing`

`totals` 示例：

```json
{
  "players": 4,
  "recordedRounds": 20,
  "plays": 20,
  "fish": 16,
  "junk": 1,
  "treasure": 3,
  "gold": 810,
  "enchanted": 2,
  "treasureRate": 15.0
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `plays` | integer | 已成功结算的每日钓鱼次数 |
| `fish` | integer | 鱼类产物次数 |
| `junk` | integer | 杂物产物次数 |
| `treasure` | integer | 宝藏产物次数 |
| `gold` | integer | 渔获兑换金粒总数，包含附魔加成，不是当前余额 |
| `enchanted` | integer | 带附魔的产物次数，多种附魔仍计一次 |
| `treasureRate` | number 或 null | `treasure / plays × 100`，未钓鱼时为 `null` |

个人记录含上述指标，以及公共字段 `userId`、`ranks`。排行榜指标为 `plays`、`fish`、`junk`、`treasure`、`gold`、`enchanted`、`treasureRate`，均按降序

一次渔获只归属一个类别，`fish + junk + treasure = plays`。按北京时间日期和用户去重，群聊与私聊共用次数；重复查看不累计。产物数量不影响钓鱼次数，例如墨囊 ×10 只计一次杂物。该接口不返回逐日渔获明细

## 卡片收藏与金粒

位置：`data.loot`

### 全局汇总

`totals` 示例：

```json
{
  "users": 100,
  "collectors": 80,
  "coins": 260000,
  "ownedCards": 3200,
  "collectedTypes": 92,
  "catalogFetchedAt": 1791259100000,
  "catalogTypes": 100,
  "normalCatalogTypes": 80,
  "collectedCatalogTypes": 90,
  "collectedNormalTypes": 76,
  "collectionCompletion": 90.0,
  "normalCollectionCompletion": 95.0
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `users` | integer | `user_loots` 中有记录的用户数，包含没有卡片的用户 |
| `collectors` | integer | 当前至少拥有一份卡片的用户数 |
| `coins` | integer | 当前金粒余额之和 |
| `ownedCards` | integer | 当前拥有卡片的总份数，重复卡按份数累计 |
| `collectedTypes` | integer | 所有用户库存中的不同物品 ID 数，跨用户去重 |
| `catalogFetchedAt` | integer | 目录最近一次成功获取时间，Unix 毫秒时间戳；没有可用目录时为 `0` |

### 个人记录

`players[]` 示例，`ranks` 仅节选一项：

```json
{
  "userId": "USER_A",
  "coins": 2680,
  "ownedCards": 45,
  "collectedTypes": 20,
  "catalogTypes": 100,
  "normalCatalogTypes": 80,
  "collectedCatalogTypes": 18,
  "collectedNormalTypes": 16,
  "collectionCompletion": 18.0,
  "normalCollectionCompletion": 20.0,
  "ranks": {"ownedCards": 12}
}
```

个人的 `coins`、`ownedCards`、`collectedTypes` 分别为当前余额、卡片份数和不同物品 ID 数。个人记录不包含 `users`、`collectors`、`catalogFetchedAt`

### 目录完成度

下列字段同时出现在全局与个人记录中：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `catalogTypes` | integer 或 null | 当前目录全部物品种类数 |
| `normalCatalogTypes` | integer 或 null | 当前目录非特殊物品的种类数 |
| `collectedCatalogTypes` | integer 或 null | 已拥有且仍在当前目录中的物品种类数 |
| `collectedNormalTypes` | integer 或 null | 已拥有且仍在当前普通目录中的物品种类数 |
| `collectionCompletion` | number 或 null | `collectedCatalogTypes / catalogTypes × 100` |
| `normalCollectionCompletion` | number 或 null | `collectedNormalTypes / normalCatalogTypes × 100` |

全局完成度使用所有用户库存中物品 ID 的并集，个人完成度使用该用户库存。已从目录移除的物品仍计入 `ownedCards` 和 `collectedTypes`，不计入目录完成度

没有可用目录时，上表六个字段均为 `null`，`unavailable` 包含 `loot.catalog`；拥有数与余额仍可返回。目录有效但种类数为零时，对应完成度为 `null`

数据来自 `user_loots.loots`，同一物品的多条库存记录合并份数。旧记录未保存份数时按一份处理。本模块提供当前拥有数，不提供历史抽卡次数、物品明细或物品图片地址

排行榜指标：`coins`、`ownedCards`、`collectedTypes`、`collectionCompletion`、`normalCollectionCompletion`

## 签到

位置：`data.sign`

### 全局汇总

`totals` 示例：

```json
{
  "todayDate": "2026-10-06",
  "totalUsers": 300,
  "totalCount": 12000,
  "totalCoins": 240000,
  "todayCount": 80,
  "todayCoins": 1600
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `todayDate` | string | 今日签到日期 |
| `totalUsers` | integer | 累计签到次数大于零的用户数 |
| `totalCount` | integer | 累计签到人次，为个人累计次数之和 |
| `totalCoins` | integer | 累计签到奖励金粒之和，不是当前余额 |
| `todayCount` | integer | 当日签到记录数，正常情况下每个用户每天一条 |
| `todayCoins` | integer | 当日签到奖励金粒之和 |

### 个人记录

今日已签到的个人记录示例：

```json
{
  "userId": "USER_A",
  "totalCount": 100,
  "totalCoins": 2000,
  "coins": 2680,
  "lastCheckInDate": "2026-10-06",
  "todayRank": 3,
  "todayCheckInTime": 1791244800000,
  "todayCoins": 20,
  "ranks": {
    "totalCount": 12,
    "totalCoins": 10,
    "todayCheckInTime": 3
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalCount` | integer | 个人累计签到次数 |
| `totalCoins` | integer | 个人累计签到奖励 |
| `coins` | integer | 当前金粒余额，可包含其他来源；没有金粒账户记录时为 `0` |
| `lastCheckInDate` | string 或 null | 最近签到日期 |
| `todayRank` | integer，可缺省 | 今日签到顺序，按当日记录的数据库 ID 升序编号 |
| `todayCheckInTime` | integer，可缺省 | 今日签到时间，Unix 毫秒时间戳 |
| `todayCoins` | integer，可缺省 | 今日获得的签到奖励 |

`players` 以累计签到表中的用户记录为准。今日未签到时，三个 `today` 字段均不出现，`ranks` 中也没有 `todayCheckInTime`

`todayRank` 是签到顺序；`ranks.todayCheckInTime` 是按签到时间计算的排行榜名次。多个用户时间戳相同时，时间榜会并列，顺序编号仍各不相同

排行榜指标：`totalCount`、`totalCoins`、`todayCheckInTime`。其中 `todayCheckInTime` 按升序，其余按降序。当前余额排行榜位于 `loot.leaderboards.coins`

## 图源投稿

位置：`data.images`

`totals` 示例：

```json
{
  "contributors": 10,
  "submitted": 100,
  "approved": 72,
  "denied": 18,
  "pending": 8,
  "other": 2,
  "approvalRate": 80.0
}
```

`players[]` 示例：

```json
{
  "userId": "USER_A",
  "submitted": 10,
  "approved": 8,
  "denied": 2,
  "pending": 0,
  "other": 0,
  "approvalRate": 80.0,
  "ranks": {"submitted": 3, "approved": 2, "approvalRate": 4}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `contributors` | integer | 仅全局包含，有投稿记录的用户去重数 |
| `submitted` | integer | 当前保留的投稿记录总数，包含所有审核状态 |
| `approved` | integer | 审核状态为 `REVIEWED` 的数量 |
| `denied` | integer | 审核状态为 `DENIED` 的数量 |
| `pending` | integer | 审核状态为 `PENDING` 的数量 |
| `other` | integer | 其他审核状态的数量 |
| `approvalRate` | number 或 null | `approved / (approved + denied) × 100`，待审核及其他状态不计入分母 |

仅统计 `image_source` 中平台为 `OFFICIAL_GROUP` 或 `OFFICIAL_C2C` 的记录，同一用户在群聊和私聊的投稿合并。删除记录不再计入，因此数量反映当前保留的记录，不是永久递增的历史投稿量

没有已通过或已拒绝记录时，通过率为 `null`。全局通过率根据全局数量计算，不是个人通过率的平均值

排行榜指标：`submitted`、`approved`、`approvalRate`

本模块不返回图片 URL、投稿内容或审核说明

## 异常与空值

### HTTP 状态与业务状态

| 情况 | HTTP 状态 | `status` | `data` |
| --- | --- | --- | --- |
| 聚合成功 | `200` | `200` | 统计对象 |
| 部分模块不可用 | `200` | `200` | 统计对象，查看 `unavailable` |
| 携带查询参数 | `400` | `432` | `null` |
| 控制器捕获到整体聚合失败 | `200` | `432` | `null` |
| 超过请求频控 | `429` | `432` | `null` |

当前控制器捕获整体失败时没有单独设置 HTTP 错误状态，因此接入端必须同时检查响应体中的 `status`

查询参数错误响应：

```json
{
  "status": 432,
  "message": "此接口不接受查询参数，请直接请求 /openapi/bot_stats",
  "data": null,
  "timestamp": 1791259201000
}
```

频控失败响应：

```json
{
  "status": 432,
  "message": "请求过于频繁，请稍后再试",
  "data": null,
  "timestamp": 1791259201000
}
```

整体聚合失败响应：

```json
{
  "status": 432,
  "message": "统计查询失败，请稍后重试",
  "data": null,
  "timestamp": 1791259201000
}
```

### 部分模块失败

`data` 节选：

```json
{
  "images": null,
  "unavailable": ["images", "loot.catalog"]
}
```

| `unavailable` 值 | 影响 |
| --- | --- |
| `registrations` | 在档数量读取失败，`registrations` 为 `null` |
| `messages` | 消息统计读取失败，`messages` 为 `null` |
| `games` | 游戏统计读取失败，整个 `games` 为 `null` |
| `loot` | 收藏或金粒统计读取失败，`loot` 为 `null` |
| `loot.catalog` | 卡片目录不可用，目录相关字段为 `null`；库存读取成功时 `loot` 仍可用 |
| `sign` | 签到统计读取失败，`sign` 为 `null` |
| `images` | 投稿统计读取失败，`images` 为 `null` |

游戏计数或库存数据格式错误也可能导致所属模块不可用。部分失败结果同样受 60 秒缓存影响

### 零、空数组、空对象与缺失字段

| 表现 | 含义 |
| --- | --- |
| 计数为 `0` | 当前没有相应记录，或历史数据中未保存该新增计数 |
| 比例或平均值为 `null` | 分母为零，无法计算 |
| 模块为 `null` | 读取失败，应结合 `unavailable` 判断 |
| `players: []` | 读取成功，但没有符合该模块条件的个人记录 |
| 某项排行榜为 `[]` | 没有数值大于零的用户 |
| `ranks: {}` | 该用户没有进入此模块的任何榜单 |
| 今日签到字段缺失 | 该用户今日没有匹配的签到记录 |

缺失细分历史数据不等同于该行为从未发生。用于任务进度时应保存开始时的计数基线；收藏数、余额和投稿库存数量会因消费或删除而减少

## 接入示例

以下 JavaScript 示例通过唯一的无参数接口读取总览和卡片拥有数排行榜：

```javascript
const response = await fetch('https://bot.example.com/openapi/bot_stats');
const result = await response.json();
if (!response.ok || result.status !== 200 || result.data == null) {
  throw new Error(result.message || 'HTTP ' + response.status);
}

const data = result.data;
const unavailable = new Set(data.unavailable);

const summary = {
  users: data.registrations?.users ?? null,
  groups: data.registrations?.groups ?? null,
  totalGroupSent: data.messages?.total.groupSent ?? null,
  todayGroupSent: data.messages?.today.groupSent ?? null,
  ownedCards: data.loot?.totals.ownedCards ?? null,
  totalSigns: data.sign?.totals.totalCount ?? null,
  todaySigns: data.sign?.totals.todayCount ?? null
};

const ownedCardsRanking = data.loot?.leaderboards.ownedCards ?? [];

if (unavailable.has('games')) {
  console.error('小游戏统计暂不可用');
}
```

排行榜与个人记录随全量响应一次返回，不提供单独的个人查询接口或 `personal` 字段。展示模块失败时应保留“不可用”状态，避免把 `null` 显示成真实的零

## 相关页面与实现

WebUI 统计页使用私有的 `/webui/api/stats/overview` 与 `/webui/api/stats/series`，页面原有日期筛选不受本接口影响。Miniapp 继续通过登录用户的 `/activity` 接口读取本人数据

本接口只返回明确列出的统计字段，不返回用户权限、群配置、绑定信息、密钥或自定义 JSON 数据

- [路由与频控](../../src/main/java/top/yzljc/atribot/webui/WebUIRouter.java)
- [响应组装与缓存](../../src/main/java/top/yzljc/atribot/webui/controller/BotStatsController.java)
- [统计聚合](../../src/main/java/top/yzljc/atribot/webui/repo/BotStatsRepository.java)
- [排名规则](../../src/main/java/top/yzljc/atribot/webui/repo/BotStatsSection.java)
- [小游戏记录口径](../features.md#小游戏统计)

