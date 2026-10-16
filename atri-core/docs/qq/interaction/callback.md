# 按钮交互

[API 目录](../../README.md) · `top.yzljc.atricore.event.events.OfficialButtonInteractionEvent`

`ButtonType.CALLBACK` 的点击通过事件接收。创建回调按钮见 [按钮键盘](../components/buttons.md)

## 监听与应答

```java
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.OfficialButtonInteractionEvent;
import top.yzljc.atricore.event.impl.AnswerCode;

public final class ButtonListener implements Listener {
    @EventHandler
    public void onClick(OfficialButtonInteractionEvent event) {
        if (!"confirm".equals(event.getButtonId())) return;
        if (event.shouldIgnore()) {
            event.answer(AnswerCode.IGNORED);
            return;
        }
        boolean answered = event.answer(AnswerCode.SUCCESS);
        if (answered) event.replyMessage("操作已确认");
    }
}
```

::: important 交互应答与消息回复
监听器需要按 [事件文档](../../events.md) 注册。`answer` 只应答交互状态，`replyMessage` 才发送聊天消息，两者分别调用
:::

## 字段

| 读取方法 | 含义 |
| --- | --- |
| `getButtonId()` | 按钮 ID，缺失时返回 `missing_data` |
| `getButtonValue()` | 按钮 data，缺失时返回 `missing_data` |
| `getChatType()` | 当前消息辅助方法支持 1 群聊、2 C2C |
| `getGroupOpenId()` / `getUserOpenId()` | 当前群及点击者标识 |
| `getId()` | 交互 ID，用于交互应答 |
| `getEventId()` | 事件 ID，用作 `RT.event` 被动回复来源 |
| `getData().getResolved()` | 原始解析数据 |
| `getScene()` / `getTimestamp()` / `getType()` / `getVersion()` | 交互场景、时间、类型和版本 |

## 操作

| 方法 | 返回与行为 |
| --- | --- |
| `answer(status)` | boolean，同步提交状态；取消事件时改为 FAIL，被忽略时改为 IGNORED |
| `shouldIgnore()` | 根据 AccessService 检查群黑名单与用户封禁、忽略状态 |
| `setCancelled(boolean)` / `isCancelled()` | 设置或读取事件取消标记 |
| `replyMessage(text / image / markdown)` | 使用当前事件作为来源，返回消息 ID |
| `replyMessage(markdown, at)` | 群聊可提及点击者 |
| `replyMessage(markdown, keyboard)` | 带键盘回复 |
| `replyMessage(markdown, keyboard, at)` | 带键盘并选择是否提及 |
| `sendMessage(text / markdown)` | 主动发送，不使用当前事件作为来源 |
| `sendMessage(markdown, keyboard)` | 主动发送 Markdown 和键盘 |

未知场景的聊天辅助方法抛出 `UnknownButtonInteractionScene`。`answer` 的请求同步执行，发送日志另行提交到共享执行器

| AnswerCode | 值 | 用途   |
| --- | --- |------|
| SUCCESS | 0 | 成功   |
| FAIL | 1 | 失败   |
| TOO_FAST | 2 | 操作过快 |
| REPEAT | 3 | 重复操作 |
| NO_PERMISSION | 4 | 无权限  |
| ONLY_ADMIN | 5 | 仅管理员 |
| IGNORED | 66 | 已屏蔽  |
| IGNORE_FAILED | 99 | 屏蔽失败 |

`AnswerCode.fromCode` 对未知数值返回 FAIL。上表描述 core 当前枚举，客户端提示与非标准状态的可用性以平台响应为准

已被屏蔽/拉黑的用户默认返回 `AnswerCode.IGNORED`
