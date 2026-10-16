# Markdown

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.Markdown`

## 创建与发送

```java
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.TC;

Markdown content = TC.md("**查询结果**\n\n")
        .append(Markdown.at(userOpenId))
        .append("\n" + Markdown.img(imageUrl, 800, 450));
String sentId = GroupChat.replyMessage(groupOpenId, RT.message(messageId), content);
```

`TC.md(text)` 等同于常用的 `new Markdown(text)`，也可使用 `new Markdown(text, forceVerifyImageResource)` 指定图片资源校验标记。省略时不提交该标记

## 格式辅助方法

| 调用 | 返回内容 |
| --- | --- |
| `img(url, width, height)` | QQ Markdown 图片标签 |
| `img(alt, url, width, height)` | 带替代文字的图片标签 |
| `at(userOpenId)` | 用户提及标签 |
| `atAll()` | 全体提及标签，实际支持情况由发送场景决定 |
| `link(url, display)` | 链接 |
| `enterCommand(command)` | 指令触发标签 |
| `enterCommand(command, display)` | 带显示文字的指令输入标签，reference 默认 false |
| `enterCommand(command, display, reference)` | 显式指定引用标记 |
| `colored(color, text)` | 以 `media.HexColor` 指定颜色的文字 |

这些方法返回 `String`，不发送消息

::: caution 指令标签的输入处理
指令和显示文字长度分别不能超过 100 个 Java 字符；方法不负责对任意输入进行 HTML 属性转义或 URL 编码
:::

`HexColor` 是保存颜色字符串的 record，可使用 `new HexColor("#336699")` 或预定义的 `RED`、`BLUE`、`GRAY` 等常量，`value()` 读取颜色值；构造器不验证颜色格式

`forceVerifyImageResource=true` 会要求图片资源校验失败时整条消息失败，null 使用平台默认行为

## 组合与键盘

| 方法 | 行为 |
| --- | --- |
| `append(String)` | 返回新的 Markdown，保留原键盘与图片校验标记 |
| `append(Markdown)` | 拼接文本；被追加对象含键盘时抛出 `UnsupportedOperationException` |
| `setKeyboard(Keyboard)` | 设置当前对象的键盘，返回当前对象 |
| `setKeyboard(Keyboard, false)` | 保留独立键盘 |
| `setKeyboard(Keyboard, true)` | 键盘转为 Markdown 文本，返回新对象，应接收返回值 |
| `withText` / `withForceVerifyImageResource` / `withKeyboard` | Lombok 生成的复制方法，返回修改后的新对象 |
| `getText` / `getForceVerifyImageResource` / `getKeyboard` | 读取字段 |
| `toString()` | 返回文本内容 |

按钮布局、权限与发送方式见 [按钮键盘](buttons.md)。在 `MessageBody` 中传 Markdown 对象时，文本序列化为 `content`，键盘字段不在 Markdown 对象内重复序列化

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### Markdown

```java
public Markdown(String text);
public static String img(String url, int width, int height);
public static String img(String alt, String url, int width, int height);
public static String enterCommand(String command);
public static String enterCommand(String command, String display);
public static String enterCommand(String command, String display, boolean reference);
public static String at(String userOpenId);
public static String link(String url, String display);
public static String atAll();
public static String colored(HexColor color, String content);
public String toString();
public Markdown append(String text);
public Markdown append(Markdown markdown);
public Markdown setKeyboard(Keyboard keyboard);
public Markdown setKeyboard(Keyboard keyboard, boolean toMarkdown);
```
