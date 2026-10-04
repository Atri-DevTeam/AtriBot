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

- 普通图片默认取图键为 `bot.image.get`，参数为 `uuid`
- 抽卡只用于 QQ 官方，取图使用 `bot.loots.draw.image` 的 `itemId`，不套用跨平台分发
- WebUI 奖励物品原图使用 `bot.loots.item.image` 的 API
- 名字与皮肤审查使用各自的普通读取、管理预览接口，不用 COS 地址替换
- SkyBlock 时间查询使用 `hypixel.skyblock.calendar`；日历生图使用 `bot.hypixel.skyblock.calendar`

WebUI 对浏览器的 `Result` 响应和外部服务的 `BizResponse` 分属不同接口层
