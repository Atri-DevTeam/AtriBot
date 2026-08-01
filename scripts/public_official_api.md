# 公开官方机器人查询接口

以下接口不需要 WebUI 登录鉴权，只查询官方机器人记录，不处理 Napcat 数据。

基础路径任选其一：

```text
/webui/api/public/official
```

所有接口返回格式统一为 `top.yzljc.atribot.webui.Result`：

```json
{
  "status": 200,
  "message": "ok",
  "data": {},
  "timestamp": 1780000000000
}
```

## 缓存

所有公开查询接口都有 1 分钟内存短缓存。缓存 key 由接口类型、时间窗口、`groupOpenId`、`userOpenId` 组成。

同一组参数在 1 分钟内重复请求会直接返回缓存结果，不重复查询数据库。缓存只影响公开查询接口，不影响 WebUI 鉴权接口、消息记录写入或机器人事件处理。

## 时间参数

消息计数和 DAU 接口支持以下查询参数：

| 参数 | 说明 |
| --- | --- |
| `start` / `startTime` / `from` | 统计开始时间，包含该时间 |
| `end` / `endTime` / `to` | 统计结束时间，不包含该时间 |
| `all=true` | 查询全量记录，忽略默认时间窗口 |
| `groupOpenId` | 可选，只统计指定群聊 |
| `userOpenId` / `unionOpenId` | 可选，只统计指定 C2C 用户 |

时间格式支持：

```text
yyyy-MM-dd
yyyy-MM-dd HH:mm:ss
ISO LocalDateTime，例如 2026-07-17T12:00:00
```

不传时间时默认统计当天：`今天 00:00:00 <= created_at < 明天 00:00:00`。

## BOT_SEND 口径

是否为本机器人发送，只能通过记录类型判断：

| 表 | 机器人发送判定 | 用户侧/接收判定 |
| --- | --- | --- |
| `official_group_record` | `event_type = 'BOT_SEND'` | `event_type <> 'BOT_SEND'` |
| `official_c2c_record` | `source = 'BOT_SEND'` | `source <> 'BOT_SEND'` |

不要用 `sender_is_bot` 判断发送/接收。

## 消息计数接口

### 群聊接收消息

```http
GET /api/public/official/group/messages/received
```

返回 `official_group_record` 中用户侧群聊消息数。

可选参数：`start`、`end`、`all`、`groupOpenId`。

### 群聊发送消息

```http
GET /api/public/official/group/messages/sent
```

返回 `official_group_record` 中 `event_type = 'BOT_SEND'` 的消息数。

可选参数：`start`、`end`、`all`、`groupOpenId`。

### C2C 接收消息

```http
GET /api/public/official/c2c/messages/received
```

返回 `official_c2c_record` 中用户侧私聊消息数。

可选参数：`start`、`end`、`all`、`userOpenId` / `unionOpenId`。

### C2C 发送消息

```http
GET /api/public/official/c2c/messages/sent
```

返回 `official_c2c_record` 中 `source = 'BOT_SEND'` 的消息数。

可选参数：`start`、`end`、`all`、`userOpenId` / `unionOpenId`。

### 返回示例

```json
{
  "status": 200,
  "message": "ok",
  "data": {
    "metric": "official_group_received_messages",
    "scope": "group",
    "startTime": "2026-07-17 00:00:00",
    "endTime": "2026-07-18 00:00:00",
    "groupOpenId": "GROUP_OPEN_ID",
    "userOpenId": null,
    "count": 641
  },
  "timestamp": 1780000000000
}
```

## DAU 接口

```http
GET /api/public/official/dau
```

返回当前统计窗口的 DAU 和四类消息计数。

字段说明：

| 字段 | 说明 |
| --- | --- |
| `dau` | 当前统计窗口内的 DAU，按用户 openId 跨群聊和 C2C 去重，只统计用户侧记录 |
| `totalDau` | 全历史每日 DAU 的平均值。先按每天计算 DAU，再对每日 DAU 求平均 |
| `groupReceiveMessages` | 当前统计窗口内群聊接收消息数 |
| `groupSendMessages` | 当前统计窗口内群聊发送消息数 |
| `c2cReceiveMessages` | 当前统计窗口内 C2C 接收消息数 |
| `c2cSendMessages` | 当前统计窗口内 C2C 发送消息数 |

返回示例：

```json
{
  "status": 200,
  "message": "ok",
  "data": {
    "startTime": "2026-07-17 00:00:00",
    "endTime": "2026-07-18 00:00:00",
    "groupOpenId": null,
    "userOpenId": null,
    "dau": 2,
    "totalDau": 7.42,
    "groupReceiveMessages": 641,
    "groupSendMessages": 33,
    "c2cReceiveMessages": 5,
    "c2cSendMessages": 7
  },
  "timestamp": 1780000000000
}
```

## 用户信息查询

```http
GET /api/public/official/users/{userOpenId}
```

返回官方 C2C 用户配置和该用户相关消息统计。

字段说明：

| 字段 | 说明 |
| --- | --- |
| `userOpenId` | 用户 openId |
| `role` | 权限角色 |
| `permissions` | 附加权限 |
| `isBlocked` | 是否拉黑 |
| `isIgnored` | 是否忽略 |
| `c2cPush` | 是否允许 C2C 主动推送 |
| `c2cReceivedMessages` | 该用户 C2C 用户侧消息数 |
| `c2cSentMessages` | 本机器人向该用户发送的 C2C 消息数 |
| `groupReceivedMessages` | 群聊中该用户作为发送者的用户侧消息数 |
| `firstSeenAt` | 首次记录时间 |
| `lastSeenAt` | 最近记录时间 |
| `lastUsername` | 最近一次用户侧记录中的用户名，不从 `BOT_SEND` 记录取值 |

## 群聊信息查询

```http
GET /api/public/official/groups/{groupOpenId}
```

返回官方群聊配置和该群消息统计。

字段说明：

| 字段 | 说明 |
| --- | --- |
| `groupOpenId` | 群 openId |
| `opMemberOpenId` | 入群/操作成员 openId |
| `timestamp` | 注册时间戳 |
| `whitelist` | 是否白名单 |
| `blacklisted` | 是否黑名单 |
| `allowedActive` | 是否允许主动消息 |
| `realGroupId` | 绑定的真实群号 |
| `receivedMessages` | 该群用户侧消息数 |
| `sentMessages` | 本机器人向该群发送的消息数 |
| `activeUsers` | 该群历史用户侧消息独立用户数 |
| `firstSeenAt` | 首次记录时间 |
| `lastSeenAt` | 最近记录时间 |
