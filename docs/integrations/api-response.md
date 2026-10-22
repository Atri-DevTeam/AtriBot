# 响应格式与业务码

[文档目录](../README.md)

使用 `Result` 封装的 WebUI、开放查询及站点授权接口采用以下结构，与 UGC 的公共响应字段及通用业务码保持一致

```json
{
  "code": 0,
  "message": "请求成功",
  "data": {},
  "timestamp": 1792540800000,
  "request_id": null
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | integer | 业务结果，0 为成功，其他值为失败 |
| message | string | 固定业务提示，或自定义错误的具体说明 |
| data | object、array、基本类型或 null | 业务数据，失败时为 null |
| timestamp | integer | 响应生成时间，Unix 毫秒时间戳 |
| request_id | string 或 null | 请求追踪标识，未建立追踪时为 null |

`code` 不表示 HTTP 状态。调用方同时检查 HTTP 请求是否成功以及 `code === 0`。旧版 `status: 200/432` 不再返回

## Java 调用

```java
ctx.json(Result.success(data));
ctx.json(Result.success(data, requestId));

ctx.status(403).json(Result.failure(ApiCode.ACCESS_DENIED));
ctx.status(400).json(Result.customFailure("该场景已存在全局面板，请编辑现有面板"));
```

固定错误使用 `Result.failure(ApiCode)`，业务码和消息由枚举定义。具体业务校验说明使用 `Result.customFailure(message)`，业务码固定为 `19999`，不得传入底层异常或敏感内容

`Result` 不接收 HTTP 状态，不修改 HTTP 响应。路由通过 `ctx.status(...)` 独立设置传输状态；`ApiCode.httpStatus` 仅提供对应错误的建议 HTTP 状态

## 通用业务码

| code | 名称 | 说明 |
| --- | --- | --- |
| 0 | SUCCESS | 请求成功 |
| 10001 | INVALID_PARAMETER | 请求参数无效 |
| 10002 | AUTHENTICATION_REQUIRED | 身份验证失败 |
| 10003 | ACCESS_DENIED | 无权执行操作 |
| 10004 | RESOURCE_NOT_FOUND | 请求资源不存在 |
| 10005 | RATE_LIMITED | 请求过于频繁 |
| 10006 | METHOD_NOT_ALLOWED | 请求方法不支持 |
| 10007 | UNSUPPORTED_MEDIA_TYPE | 请求内容类型不支持 |
| 10008 | RESOURCE_CONFLICT | 资源状态冲突 |
| 10009 | PAYLOAD_TOO_LARGE | 请求内容过大 |
| 10010 | NOT_ACCEPTABLE | 响应内容类型不支持 |
| 19999 | CUSTOM_ERROR | 自定义业务错误，具体原因见 message |
| 40005 | IMAGE_PROCESSING_FAILED | 图片处理失败 |
| 40006 | IMAGE_DELETE_FAILED | 图片删除失败 |
| 60001 | LOOT_ITEM_NOT_FOUND | 奖励物品不存在 |
| 90001 | INTERNAL_ERROR | 服务器内部错误 |
| 90002 | SERVICE_UNAVAILABLE | 服务暂不可用 |
| 90003 | UPSTREAM_FAILED | 上游服务请求失败 |
| 90004 | UPSTREAM_TIMEOUT | 上游服务请求超时 |

以上业务码与 UGC 同名功能对齐，不从 HTTP 状态推导

## 本站业务码

| code | 名称 | 说明 |
| --- | --- | --- |
| 70001 | QQ_API_REQUEST_FAILED | QQ 平台接口请求失败 |
| 70002 | QQ_RESPONSE_INVALID | QQ 平台响应格式无效 |
| 71001 | WELCOME_SEND_FAILED | 加群欢迎发送失败 |
| 72001 | PUSH_TASK_NOT_FOUND | 推送任务不存在 |
| 73001 | STATISTICS_QUERY_FAILED | 统计查询失败 |
| 80001～80013 | 站点授权 | 详见[站点授权错误码](site-authorization.md#错误码) |

业务码定义位于 [ApiCode.java](../../src/main/java/top/yzljc/atribot/webui/ApiCode.java)，响应封装位于 [Result.java](../../src/main/java/top/yzljc/atribot/webui/Result.java)
