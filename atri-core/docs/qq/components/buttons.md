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
        TC.md("请选择操作"), keyboard.toKeyboardObject());
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
| `toKeyboardObject()` | 转换为发送接口接受的对象 |
| `toMarkdownString()` | 仅支持 COMMAND 和 LINK；CALLBACK 或 CUSTOM 会抛出 UnsupportedOperationException |
| `TC.keyboard(rows)` / `TC.keyboard(rows, size)` | 直接生成键盘对象 |
| `TC.promptKeyboard(rows)` | 直接生成输入提示键盘对象 |

`TC` 的直接转换方法不执行 `Keyboard` 构造器的行列数量校验。发送时使用转换后的对象或将 Keyboard 绑定至 Markdown，不直接将 Keyboard 当作平台请求结构

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
public Keyboard setSize(ButtonSize size);
public Keyboard setPromptStyle(boolean isPrompt);
public Object toKeyboardObject();
public String toMarkdownString();
public Markdown toMarkdown();
```

### TC

```java
public static Markdown md(String text);
public static Object promptKeyboard(List<List<Button>> layout);
public static Object keyboard(List<List<Button>> layout);
public static Object keyboard(List<List<Button>> layout, ButtonSize size);
```
