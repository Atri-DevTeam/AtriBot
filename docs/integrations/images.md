# 图片与业务接口

[文档目录](../README.md) · [平台与消息](../framework/platforms.md)

## 接口目录

`ugc-api-url` 配置服务根地址，可包含部署前缀，不包含具体业务路径：

```yaml
ugc-api-url: "https://images.example.com"
```

[OpenApi](../../src/main/java/top/yzljc/atribot/service/request/OpenApi.java) 从根地址下的 `/v3/openapi` 加载目录，业务使用目录键取得接口：

```java
String endpoint = OpenApi.get("bot.hypixel.bedwars");
String method = OpenApi.method("bot.hypixel.bedwars");
```

目录首次使用时加载，缓存 5 分钟。并发刷新合并为一次请求；过期刷新失败时可使用同地址的旧目录，30 秒后允许重试。首次加载失败或键不存在时抛出 `IllegalStateException`

`OpenApi.refresh()` 主动刷新目录，失败时抛异常并保留旧缓存。目录刷新不重发业务请求。`availableServices()` 返回服务声明的分发方式，不表示服务健康状态

## 图源投稿与审核

图源投稿、审核、删除和图片分发使用 UGC pic 服务，接口由 OpenAPI 目录解析，不在客户端固定版本路径

| 业务键 | 用途 |
| --- | --- |
| `pic.upload` | 提交 `uuid`、`sourceUrl`，读取处理后的 `width`、`height`、`file_size` |
| `pic.status` | 同步 `PENDING`、`REVIEWED`、`DENIED` 审核状态 |
| `pic.delete` | 删除图片 |
| `pic.delivery` | 查询当前可用的分发地址 |
| `pic.image` | 获取图片本体，与响应中的 `api_url` 对应 |

鉴权使用项目统一的 `atribot-key-secret`，对应 UGC 的 `atri.atribot-key.secret`。`image-source.enabled` 控制投稿入口，`pending-limit` 控制每位用户的待审数量。原 `image-source.token`、`api-url`、`view-base-url` 不再使用，服务统一由 `ugc-api-url` 指定，该地址需要能被 WebUI 浏览器访问

机器人默认按 UGC 返回的 COS → CNB → API 顺序选用已就绪地址，不缓存 COS 签名或 CNB 地址前缀。传入非 QQ 官方平台时直接使用 API 地址。WebUI 列表、预览和原图链接始终使用 `pic.image`，不调用分发查询，也不使用 COS/CNB 地址

本地审核记录仍保存在原图源表，远端操作确认成功后才更新本地状态。历史图片需在 UGC 的 pic 存储目录中保留原 UUID，本次接口调整不迁移图片文件。个人背景继续使用独立的 `bot.background.*` 接口

## 本地资源配置

[RS](../../src/main/java/top/yzljc/atribot/configuration/RS.java) 按完整键名读取 `request.json` 中的资源地址：

```java
String image = RS.get("resource.image.hypixel-header");
```

配置在首次使用时加载并缓存，修改后需重启。键名不存在或值为空时抛出 `IllegalStateException`，地址中的占位符由调用方替换

## 请求结果

[Requests](../../src/main/java/top/yzljc/atribot/service/request/Requests.java) 的 JSON 请求返回 `BizResponse<JsonNode>`。成功要求 HTTP 为 2xx 且业务 `code` 为整数 `0`

| 字段 | 内容 |
| --- | --- |
| `httpCode` | HTTP 状态，无响应时为 `0` |
| `bizCode` | 业务码，无有效业务码时为 `-1` |
| `message` | 服务端提示 |
| `requestId`、`timestamp` | 请求标识与时间 |
| `data` | 业务数据，允许为空 |

请求不自动重试，不跟随重定向。非 2xx 响应中的非空 `message` 仍被保留。调用方在检查 `isSuccess()` 后，还需校验自己所需的字段

## 生图调用

[PreImageGenerate](../../src/main/java/top/yzljc/atribot/function/impl/PreImageGenerate.java) 将业务响应转换为 `ImageDTO`：

```java
ImageDTO image = PreImageGenerate.dump(endpoint, Map.of("player", player));
ImageDTO platformImage =
        PreImageGenerate.dump(endpoint, Map.of("player", player), platform);
ImageDTO cosImage =
        PreImageGenerate.dump(endpoint, Map.of("player", player), "cos");
```

`ImageDTO` 提供 `url`、`width`、`height`、`errorMessage`、`traceId`。生成和转存失败通过错误字段返回，调用方应先检查失败状态再发送图片

## 图片地址选择

| 调用方式 | 行为 |
| --- | --- |
| 默认重载，不传平台 | 遵循服务返回方式，优先使用完整 `url` |
| 传 QQ 官方平台 | 与默认重载相同 |
| 传 Napcat、Discord、KOOK 平台 | 强制使用同源 `api_url` |
| 第三个参数为 `"api"` | 强制使用同源 `api_url` |
| 第三个参数为 `"cos"` | 要求 `way=cos` 且有有效完整 `url` |
| `dumpViaApi(endpoint, body)` | 强制 API |

非官机路径应传入目标 `Platform`，多平台命令可传 `sender.getPlatform()`。已经限定为 QQ 官方的业务可使用默认重载。生图工具不接收发送者对象

API 方式只接受有效的同源路径。缺少或无效的 `api_url` 返回图片错误，不回退到 COS / OSS。默认方式遇到 `way=cos` 但没有有效 URL 也返回失败，COS 签名参数完整保留

显式 `way` 重载只选择响应中的地址，不修改生图请求参数。支持 `cos`、`api`，忽略大小写及首尾空白；无效值在请求前抛出 `IllegalArgumentException`。使用默认行为时省略第三个参数，传空平台时需写 `(Platform) null` 避免重载歧义

地址解析实现见 [ImageDelivery](../../src/main/java/top/yzljc/atribot/configuration/ImageDelivery.java)

## 独立业务约定

### QQ 官方文档更新

`QqBotDocsMonitor` 通过 `bot.docs.diff` 请求 UGC 渲染差异图。每篇文档携带新增、删除、上下文行及原文和现文行号；修改段前后保留两行上下文。新增及删除文档包含完整正文

一次提交整轮变更，UGC 返回标准单图结构。所有文档连续排在同一张长图中，每轮向 `open_platform_doc_check` 的订阅者推送一张图片，并附官方文档入口。长行自动换行，绿色表示新增、红色表示删除。取图遵循 QQ 官机的服务端分发配置

完整长图生成后发送一次推送。接口未部署、请求超限或生图失败时，本轮回退到文字报告；完整文字报告仍写入 `data/qqbot-docs-monitor/latest-report.txt`。部署时需同时更新 UGC 与机器人，UGC 目录键名为 `bot.docs.diff`，无需新增机器人配置项

- 普通图片默认取图键为 `bot.image.get`，参数为 `uuid`
- 抽卡只用于 QQ 官方，取图使用 `bot.loots.draw.image` 的 `itemId`，不套用跨平台分发
- WebUI 奖励物品原图使用 `bot.loots.item.image` 的 API
- 名字与皮肤审查使用各自的普通读取、管理预览接口，不用 COS 地址替换
- SkyBlock 时间查询使用 `hypixel.skyblock.calendar`；日历生图使用 `bot.hypixel.skyblock.calendar`

WebUI 对浏览器的 `Result` 响应和外部服务的 `BizResponse` 分属不同接口层


## 自定义背景审核

`/bg` 与图片附件一并发送，每次接收第一张图片。审核通过或拒绝均不主动通知用户；单独发送 `/bg` 查看最近一次申请状态、编号、提交时间、审核时间及审核说明，`/bg clear` 清除当前背景及最新申请。投稿成功后通过 `Alert.notify` 提醒调试群。自定义背景暂仅支持 QQ 官机，已接入的查询见下表

WebUI「图片审核」包含图源审核和背景图审核。背景申请支持通过、拒绝、撤销审核、批量审核和删除；接口及预览沿用 WebUI 登录鉴权

### 状态与存储

背景设置存于 `bot_userdata.user_settings.background`，与其他个人设置共用 JSON 字段

```json
{
  "activeId": "已通过背景的 UUID，未设置时为 null",
  "submission": {
    "id": "最新申请的 UUID",
    "status": "PENDING",
    "uploaderName": "投稿人",
    "groupId": "来源群，私聊时为 null",
    "platform": "QQ",
    "width": 1920,
    "height": 1080,
    "size": 180000,
    "createdAt": 1791698400000,
    "reviewedAt": 0,
    "reviewer": null,
    "remark": null
  }
}
```

- 每人保留已通过背景和最新申请，最多引用两份文件，不保存完整申请历史
- 存在待审核申请时拒绝再次投稿；新申请与拒绝操作均保留原背景
- 通过审核后替换 `activeId`，清理旧背景；撤销当前生效图片的审核后立即停用该图片
- 删除最新申请只移除该申请；它同时是生效背景时一并停用。`/bg clear` 清除全部背景设置
- 更新使用用户行锁事务，仅写入 `background` 节点。远程图片上传和文件清理在数据库事务外执行
- 管理列表在数据库中按 JSON 审核状态筛选、分页，不将全部用户或图片装载到应用内存；目前不新增 JSON 索引

### UGC 存储与资源限制

沿用 `ugc-api-url` 和 `atribot-key.secret` 访问 UGC。上传、读取、清理均使用私有接口，不公开待审图地址；WebUI 通过本项目鉴权接口获取图片，浏览器不接触 UGC 密钥

- 允许 PNG、JPEG、GIF、WebP，GIF 和动态 WebP 仅保留首帧
- 单张原图最大 12 MiB，读取过程中限制字节数；超出存储分辨率时自动降采样，解码器保留压缩比异常等校验
- 保存为最大 3840 × 2160（4K）的等比例 JPEG，透明区域以白色合成，移除原始元数据；审核缩略图最大 480 × 270
- 文件持久化到 UGC 磁盘，不进入公共素材缓存、图片分发缓存或 COS
- 主项目最多同时处理两次背景投稿；UGC 默认最多同时处理两次上传或背景解码
- UGC 的 `BackgroundStore.withImage` 按需读取，在回调结束后释放图片；上述查询共用两个绘制与编码额度，个人背景与个性化成图不进入渲染器共享缓存
- 审核列表加载缩略图，打开预览后才加载大图；主项目限制同时代理四次图片读取
- 正常替换、清除后删除无用文件；清理失败会记录日志。后续背景变更会同步引用，补清理超过一小时且不再引用的文件，包括中断上传残留

部署时先更新 UGC，再更新机器人。UGC 存储目录必须持久化，并与机器人数据库一并备份

上述查询接口的 `hash` 为本次最终 PNG 字节的 SHA-256，不再使用背景素材摘要。未设置已审核背景时使用默认背景；背景文件读取失败时记录日志并回退默认背景。KOOK 继续使用默认背景与 API 取图方式

### 已接入的查询

| 指令 | 查询内容 |
| --- | --- |
| `/hyp ip` | SkyBlock 物品价格，含指令按钮翻页 |
| `/hyp dungeon` | 地牢记录 |
| `/hyp wz` | 法师掘战 |
| `/hyp zs` | 僵尸末日 |
| `/hyp gs [小游戏]` | Hypixel 在线人数总览或指定小游戏 |
| `/hyp bw` | 起床战争 |
| `/hyp sh` | 入梦酒店任务树 |
| `/hyp dpr` | 心跳水立方 |
| `/hyp lf` | 大厅钓鱼 |
| `/hyp pr` | 大厅跑酷 |
| `/hyp coop` | SkyBlock Coop 成员 |
| `/hyp hotf` | 树之心，含存档切换 |
| `/hyp hotm` | 山之心，含存档切换 |
| `/hyp cr` | SkyBlock 活动日历 |
| `/hyp pack`、`/skbpack` | SkyBlock 资源包版本 |
| `/bantrack` | Hypixel 封禁统计 |
| `/mccape`、`/mctool cape` | Minecraft 披风统计 |
| `/mctool pf [玩家名或UUID]` | Minecraft 玩家档案，包含审核后的玩家名、皮肤及披风记录；QQ 可省略参数使用绑定玩家 |

背景取发起查询者的已审核设置，与被查询的 Minecraft 玩家无关。请求仅携带 `background.owner` 和 `background.id` 引用，不重复上传图片。价格翻页、存档切换和时间范围切换均重新读取当前生效背景

资源包定时推送使用默认背景。其他平台及未列出的查询保持原有背景，Mojang 服务状态不接入
