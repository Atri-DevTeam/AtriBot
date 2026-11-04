# 私聊消息操作栏

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.button.ActionButton`

`ActionButton` 配置消息上的赞踩、语音播放和重新生成等操作，序列化为消息请求的 `action_button` 字段

::: important 仅私聊
消息操作栏仅用于 QQ 官方机器人 C2C 私聊。群聊发送类及群聊专用事件不提供此重载；`QQCommandSender` 和 `OfficialButtonInteractionEvent` 共用发送入口在群聊中忽略操作栏参数，正文仍按原方式发送
:::

## 创建与发送

```java
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.ActionButton;

var actions = new ActionButton()
        .setFeedback(true)
        .setReGenerate(true)
        .setCallbackData("query_result");

String sentId = C2CChat.replyMessage(userOpenId, RT.message(messageId),
        TC.md("查询完成"), actions);
```

`ActionButton` 是普通类，提供无参构造、getter、链式 setter 和 `builder()`。只需设置本次使用的字段，未设置的字段不写入 JSON；布尔字段显式设置为 `false` 时会写入 `false`

| 链式方法 | 请求字段 | 说明 |
| --- | --- | --- |
| `setFeedback(value)` | `feedback` | 赞踩按钮 |
| `setTts(value)` | `tts` | 语音播放按钮 |
| `setReGenerate(value)` | `re_generate` | 重新生成按钮 |
| `setStopGenerate(value)` | `stop_generate` | 停止生成按钮 |
| `setCallbackData(data)` | `callback_data` | 操作回调数据，最多 128 个 Unicode 码点；超出时抛出 IllegalArgumentException |

上述示例中的操作栏序列化为：

```json
{
  "action_button": {
    "feedback": true,
    "re_generate": true,
    "callback_data": "query_result"
  }
}
```

## 发送入口

文本、`Markdown` 和 `ImageComponent` 的发送方法可在原参数末尾追加 `ActionButton`，回复来源、消息引用与错误处理沿用原方法

| 入口 | 调用方式 |
| --- | --- |
| `C2CChat` / `AsyncC2CChat` | `sendMessage`、`replyMessage`、`refMessage`、`wakeupMessage` 的操作栏重载 |
| `QQUser` | 私聊 `sendMessage` 重载，包括使用 RT 和引用的发送入口 |
| `QQCommandSender` | `sendMessage(content, actions)`，仅私聊携带操作栏 |
| `OfficialC2CMessageCreateEvent` | `sendMessage(content, actions)`，使用当前消息 ID 被动回复 |
| `OfficialFriendAddEvent` | `sendOpeningMessage(content, actions)`，使用好友添加事件 ID 发送 |
| `OfficialButtonInteractionEvent` | `replyMessage(content, actions)` 或 `sendMessage(content, actions)`，仅私聊携带操作栏 |
| `OfficialC2CPromptFeedbackEvent` | `sendMessage(content, actions)`，使用当前反馈事件 ID 被动回复 |

`actions` 为 `null` 时不额外设置操作栏。文件、语音、Ark、Card 和流式消息的专用发送方法不提供操作栏重载

普通消息键盘通过 `Markdown.setKeyboard(Keyboard)` 设置，可与操作栏同时使用，详见 [按钮键盘](buttons.md)

## 接收操作反馈

操作反馈通过 `OfficialC2CPromptFeedbackEvent` 接收，与普通回调按钮使用的 `OfficialButtonInteractionEvent` 分开处理

```java
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.ActionButton;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.OfficialC2CPromptFeedbackEvent;

public final class ResultFeedbackListener implements Listener {
    @EventHandler
    public void onFeedback(OfficialC2CPromptFeedbackEvent event) {
        if (!"query_result".equals(event.getCallbackData())) return;
        if (!OfficialC2CPromptFeedbackEvent.REGENERATE.equals(event.getFeedbackOpt())) return;

        var actions = new ActionButton()
                .setReGenerate(true)
                .setCallbackData("query_result");
        event.sendMessage(TC.md("结果已更新"), actions);
    }
}
```

监听器需要按 [事件文档](../../events.md#注册监听器) 注册。示例回复固定文本，实际重新查询或生成由运行服务处理；耗时任务应交给调度器执行

| 读取方法 | 含义 |
| --- | --- |
| `getUserOpenId()` | 操作者的私聊用户 OpenID |
| `getEventId()` | 本次反馈事件 ID，发送辅助方法使用此值作为回复来源 |
| `getTargetMessageId()` | 用户操作的机器人消息 ID，对应回调中的 `message_id` |
| `getCallbackData()` | 回调中的 `button_data`，用于识别发送时设置的业务数据 |
| `getFeedbackOpt()` | 操作类型，常量见下表；缺失时为 null |
| `getChecked()` | 1 选中、2 取消选中；字段缺失时为 0 |
| `getMessageScene()` | `message_scene` 原始 JSON |
| `getDataType()` / `getChatType()` | 平台上报的数据类型及会话类型 |

| 常量 | 值 |
| --- | --- |
| `LIKE` | `LIKE` |
| `UNLIKE` | `UNLIKE` |
| `REGENERATE` | `REGENERATE` |
| `PROMPT_CLICK` | `PROMPTCLICK` |

::: note 回复来源
`getTargetMessageId()` 指向机器人此前发送的消息，不是用户新发消息的 ID。回复操作反馈使用 `event.sendMessage(content, actions)`；无需附带操作栏时，第二个参数传 `null`
:::

反馈事件的 `sendMessage` 对文本、Markdown 和 ImageComponent 附加操作栏；传入 Card 时按卡片发送，不附加操作栏。未知类型按 `toString()` 转为文本，null 内容按空文本处理
