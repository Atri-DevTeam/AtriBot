o# 图片服务接口目录

图片服务地址由 `config.yml` 中的 `ugc-api-url` 配置，填写服务根地址，不包含接口版本或业务路径：

```yaml
ugc-api-url: "http://127.0.0.1:1234"
```

支持带部署前缀的根地址，例如 `https://example.org/images`。客户端在根地址后拼接目录路径 `/v3/openapi`，业务路径全部使用目录返回值。

## 调用方式

```java
String url = OpenApi.get("bot.hypixel.bedwars");
ImageDTO image = PreImageGenerate.dump(url, Map.of("player", player));
```

`OpenApi.get(key)` 返回完整 URL；`OpenApi.method(key)` 返回请求方法；`OpenApi.availableServices()` 返回当前部署声明的图片发布方式。发布方式列表不代表上游健康状态，请求仍使用原有请求头和参数。

目录首次使用时加载，缓存 5 分钟。并发加载只发起一次目录请求。过期刷新失败时保留同一服务地址的已有目录，并在 30 秒后允许重试；首次加载失败或键名不存在时抛出 `IllegalStateException`。修改服务根地址后不会复用旧地址的目录。

调用 `OpenApi.refresh()` 可立即刷新目录，失败时抛出异常并保留已有缓存。目录刷新不会自动重发图片生成请求。

## 响应与图片地址

本后端的 JSON 请求使用 `Requests.get/post/put/delete/multipart`，统一返回 `BizResponse<JsonNode>`。仅在 HTTP 为 2xx 且业务 `code` 为整数 `0` 时 `isSuccess()` 返回 true。`httpCode` 保留 HTTP 状态，`bizCode` 保留业务码，`message`、`requestId`、`timestamp` 保留服务端响应，`data` 保留完整业务数据。未收到 HTTP 响应时 `httpCode` 为 0，缺少有效业务码时 `bizCode` 为 -1。请求不会自动重试，也不跟随重定向。

`PreImageGenerate.dump` 只负责把业务结果转换为 `ImageDTO`，错误消息及请求标识分别放入 `errorMessage`、`traceId`，不再维护独立响应协议。成功响应允许 `data` 为 null，例如删除物品成功；需要图片或列表数据的调用方另行校验数据结构。

本地图片通过 `bot.image.get` 的 `{uuid}` 定位；抽卡结果图片通过 `bot.loots.draw.image` 的 `{itemId}` 定位。响应中的旧相对地址不作为新版接口路径来源。签名图片直接使用本次响应中的完整 `url`，缺少签名地址时返回失败。

`Requests` 统一发送请求并解析非 2xx 的业务响应，不再依赖旧 `HttpService` 的简化方法。第三方接口继续使用原有客户端。

非 2xx 响应即使缺少业务码，只要包含非空字符串 `message`，也会保留该提示并判定请求失败。图片生成和转存的 `dump` 重载均通过 `ImageDTO.errorMessage` 返回失败原因，不再将转存失败转换为 null。公告配图、奖励配图失败时记录原因并继续发送正文。

已迁移图片生成、图片转存、卡池列表、奖励物品管理、玩家资料与审核后资料查询。上述业务统一使用 `ugc-api-url` 配置的后端根地址，不再使用 `api-url` 拼接旧路径。已有鉴权密钥继续使用原配置。

WebUI 的奖励物品原图始终使用 `bot.loots.item.image` 对应的 API 地址，接口直接返回后端本地 PNG，不使用生成结果中的 COS 签名地址。`imageBaseUrl` 和 WebUI 对浏览器的 `Result` 协议保持原样。物品管理失败会展示后端返回的消息。

名字与皮肤审核客户端使用 `OpenApi` 获取新版接口地址，通过 `Requests` 和 `BizResponse` 处理响应。鉴权配置、分页与状态筛选、提交及审核字段保持原样，JSON 调用仍将 `data` 交给原业务读取。头像与皮肤图使用 `Requests.getBytes` 读取 API 原始字节；普通读取与管理员预览仍使用各自接口，保留后端原有审核与默认图片规则，不改为 COS 获取。WebUI 对浏览器的响应格式保持原样，非 2xx 响应保留后端错误消息。

B 站关注验证不属于当前后端目录，保留原调用。已删除的帮助图、赞助图、玩家统计卡不再保留兼容请求。

SkyBlock 时间查询使用 `Requests.get(OpenApi.get("hypixel.skyblock.calendar"))`，对应 `GET /v3/hypixel/skyblock/calendar`，返回 `BizResponse<JsonNode>`。该调用与生图共用 `ugc-api-url` 根地址；`data.skyblockTime`、`data.activeEvents` 的字段与展示含义保持不变。`bot.hypixel.skyblock.calendar` 则是同一路径的 POST 生图接口，两者使用不同业务键。
