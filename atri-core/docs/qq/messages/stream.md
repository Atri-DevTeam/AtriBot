# C2C 流式消息

[API 目录](../../README.md) · `C2CChat` / `AsyncC2CChat`

流式消息使用 `top.yzljc.atricore.chat.official` 包的聊天入口。`C2CStreamMessage` 是包内实现，不能从运行服务直接调用

## 回复文本流

```java
import top.yzljc.atricore.chat.official.AsyncC2CChat;
import top.yzljc.atricore.chat.official.RT;
import java.util.List;

var future = AsyncC2CChat.replyTextStreamDeltas(userOpenId,
        RT.message(messageId), List.of("正在整理结果", "\n第一项", "\n第二项"));
future.whenComplete((sentId, failure) -> {
    if (failure != null) {
        System.err.println("流式发送失败: " + failure.getMessage());
    } else if (sentId == null) {
        System.err.println("未获得流式消息 ID");
    }
});
```

## 公开入口

| 方法 | 内容参数 | 回复来源 |
| --- | --- | --- |
| `replyTextStreamDeltas(openId, rt, deltas)` | `List<String>` 文本增量 | `RT.message` 或 `RT.event` |
| `replyStreamDeltas(openId, rt, deltas)` | `List<Markdown>` Markdown 增量 | `RT.message` 或 `RT.event` |
| `wakeupTextStreamDeltas(openId, deltas)` | `List<String>` | 召回发送，不携带来源 |
| `wakeupStreamDeltas(openId, deltas)` | `List<Markdown>` | 召回发送，不携带来源 |
| `streamTextDeltas(openId, deltas)` | `List<String>` | 当前没有来源，内部校验返回 null |
| `streamDeltas(openId, deltas)` | `List<Markdown>` | 当前没有来源，内部校验返回 null |

上述方法在 `C2CChat` 返回 `String`，在 `AsyncC2CChat` 返回 `CompletableFuture<String>`。普通流式回复应使用带 `rt` 的方法；召回发送需要平台授权，不能当作无限制的主动消息

## 增量与发送状态

::: info 增量内容的含义
传入的是新增文本片段，不是每一步的完整内容。例如 `List.of("你", "好")` 依次形成 `你`、`你好`，内部以 `replace` 模式发送完整快照
:::

列表在调用时提供完整；本接口不接受边生成边提交的订阅流。null 和空文本片段会被过滤，最终没有内容时返回 null

内部生成连续索引，首条响应的消息 ID 用于后续更新；中间状态为 1，结束状态为 10。发送采用约 55 秒的安全预算，必要时跳过中间快照，优先提交最终内容；这不是对 HTTP 调用耗时的硬超时保证

## 结果与错误

::: important 消息 ID 与结束状态
返回值是最近一次成功发送得到的消息 ID。某次发送返回空结果时可能返回此前的 ID，因此非 null 不能单独证明结束状态已提交
:::

平台拒绝可能抛出 `QQMessageSendException`，异步入口以异常完成表示；空内容、缺少来源或部分解析失败可以返回 null。应同时处理异常和空结果，错误类型见 [发送约定](conventions.md)

Markdown 构造方式见 [Markdown](../components/markdown.md)，本批处理入口消费 Markdown 文本，不用于携带按钮键盘
