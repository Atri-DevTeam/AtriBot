# 按钮键盘

[API 目录](../../README.md) · 包 `top.yzljc.atricore.chat.official.button`

## 发送按钮

```java
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.*;
import java.util.List;

var query = new Button("query", "查询", "/query", true,
        ButtonStyle.BLUE, ButtonType.COMMAND);
var confirm = new Button("confirm", "确认", "confirm:123",
        ButtonStyle.RED, ButtonType.CALLBACK)
        .setAllowedOpenIds(List.of(userOpenId))
        .setModal("是否执行此操作？", "确认", "取消");
var keyboard = new Keyboard(List.of(List.of(query, confirm)));
String sentId = GroupChat.replyMessage(groupOpenId, RT.message(messageId),
        TC.md("请选择操作").setKeyboard(keyboard));
```

## 按钮构造参数

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `buttonId` | String | 按钮标识，同一消息内应能区分不同操作 |
| `displayText` | String | 显示文字，默认也是点击后的文字 |
| `data` | String | 链接地址、回调数据或指令文本，由 actionType 决定 |
| `enter` | boolean | 指令按钮是否直接发送，省略构造参数时默认 true |
| `style` | ButtonStyle | 按钮样式 |
| `actionType` | ButtonType | 按钮行为 |

| `ButtonType` | 值 | 行为 |
| --- | --- | --- |
| LINK | 0 | 打开链接 |
| CALLBACK | 1 | 产生交互回调 |
| COMMAND | 2 | 输入或发送指令 |
| CUSTOM | 999 | 当前枚举保留值，不表示平台普遍支持 |

| `ButtonStyle` | 值 |
| --- | --- |
| GRAY | 0 |
| BLUE | 1 |
| ICON_BUTTON | 2 |
| RED | 3 |
| BLUE_WITH_BACKGROUND | 4 |

## 行为、权限与确认框

| 链式方法 | 说明 |
| --- | --- |
| `setVisitedDisplayText(text)` | 点击后显示文字 |
| `setEnter(value)` | 修改指令是否直接发送 |
| `setReply(value)` | 设置指令回复标记，默认 false |
| `setPermissionType(type)` | `ALL` 全体、`ADMIN` 管理员、`SPECIFIC_USER` 指定用户 |
| `setAllowedOpenIds(ids)` | 设置允许用户，并自动切换为 SPECIFIC_USER |
| `setModal(content)` | 设置二次确认内容 |
| `setModal(content, confirmText, cancelText)` | 自定义确认与取消文字，两者各最多 4 个 Java 字符 |
| `setUnsupportedTip(text)` | 不支持时的提示 |
| `setButtonGroupId(id)` | 设置平台按钮分组标识 |

权限类型对应平台数值 2、1、0。按钮权限影响客户端与平台交互，业务处理回调时仍需检查目标操作的实际权限

## 行列与序列化

外层列表表示行，内层列表表示一行的按钮；`Keyboard` 构造时最多 5 行、每行最多 10 个按钮，超出会拒绝构造

| 入口 | 说明 |
| --- | --- |
| `new Keyboard(rows)` | 默认布局 |
| `new Keyboard(rows, size)` | 指定 `ButtonSize.SMALL` 或 `UNDEFINED` |
| `new Keyboard(rows, size, promptStyle)` | 是否使用输入提示键盘 |
| `setSize(size)` / `setPromptStyle(value)` | 修改布局选项 |

通过 `markdown.setKeyboard(keyboard)` 设置按钮，发送层负责转换为 QQ 键盘协议

`Keyboard.of(rows)` 与构造方法等价；`Keyboard.single(button)` 创建单按钮键盘，`Keyboard.singleLine(buttons)` 创建单行键盘。`append(rows)` 在末尾追加行，`append(rows, true)` 在开头追加行，两者都返回新键盘，保留原尺寸与提示样式，并校验合并后的行列数量

## 输入提示键盘

::: important 仅私聊
`setPromptStyle(true)` 用于 QQ 官方机器人 C2C 输入提示键盘。群聊使用普通消息键盘，保留默认的 `false`
:::

```java
var keyboard = Keyboard.single(new Button("query", "查询", "/query", true,
        ButtonStyle.BLUE, ButtonType.COMMAND)).setPromptStyle(true);
C2CChat.replyMessage(userOpenId, RT.message(messageId),
        TC.md("请选择操作").setKeyboard(keyboard));
```

示例中的 `C2CChat` 位于 `top.yzljc.atricore.chat.official` 包。普通键盘写入请求的 `keyboard`，输入提示键盘写入 `prompt_keyboard.keyboard`

消息下方的赞踩、语音播放、重新生成等操作使用 [ActionButton](action-button.md)，与本页的按钮键盘分别配置

## 对象与协议

`Keyboard` 保存按钮行、尺寸和提示样式。Jackson 序列化键盘时由 `KeyboardPayload.Serializer` 转换为 QQ 请求结构，发送层通过 `KeyboardPayload.from(keyboard)` 取得独立的协议快照，业务发送无需自行转换

::: tip 原有调用迁移
旧的 `Object keyboard` 发送参数、`TC.keyboard(...)` 和 `TC.md(text, keyboard)` 已移除。使用 `new Keyboard(rows)` 构造键盘，再通过 `TC.md(text).setKeyboard(keyboard)` 随 Markdown 发送。`Keyboard` 不再提供 `toPayload()`、`fromPayload()` 或转换 Markdown 文本的兼容方法
:::

回调按钮的接收、应答和消息回复见 [按钮交互](../interaction/callback.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### Button

```java
public Button(String buttonId, String displayText, String data, ButtonStyle style, ButtonType actionType);
public Button(String buttonId, String displayText, String data, boolean enter, ButtonStyle style, ButtonType actionType);
public Button setReply(boolean reply);
public Button setVisitedDisplayText(String visitedDisplayText);
public Button setPermissionType(PermissionType permissionType);
public Button setAllowedOpenIds(List<String> allowedOpenIds);
public Button setEnter(boolean enter);
public Button setModal(String content);
public Button setUnsupportedTip(String unsupportedTip);
public Button setModal(String content, String confirmText, String cancelText);
public Button setButtonGroupId(String buttonGroupId);
```

### Keyboard

```java
public Keyboard(List<List<Button>> buttons);
public Keyboard(List<List<Button>> buttons, ButtonSize size);
public Keyboard(List<List<Button>> buttons, ButtonSize size, boolean isPromptStyle);
public static Keyboard of(List<List<Button>> buttons);
public static Keyboard single(Button button);
public static Keyboard singleLine(List<Button> buttons);
public Keyboard setSize(ButtonSize size);
public Keyboard setPromptStyle(boolean isPrompt);
public Keyboard append(List<List<Button>> buttons);
public Keyboard append(List<List<Button>> buttons, boolean reverse);
```

### TC

```java
public static Markdown md(String text);
```
