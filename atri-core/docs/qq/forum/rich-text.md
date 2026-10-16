# 频道富文本

[API 目录](../../README.md) · 包 `top.yzljc.atricore.chat.official.thread`

RichText 由段落 Paragraph 组成，段落由元素 Elem 组成，保留频道特有的内容结构

```java
import top.yzljc.atricore.chat.official.thread.*;

RichText content = new RichText()
        .paragraph(new Paragraph().alignment(Alignment.MIDDLE)
                .text("查询结果", TextStyle.BOLD))
        .paragraph(new Paragraph().image(imageUrl, 0.8))
        .blankLine()
        .paragraph(new Paragraph().url("https://example.com", "详情"));
String json = content.toJson();
RichText parsed = RichText.fromJson(json);
```

## 文档与段落

| 入口 | 行为 |
| --- | --- |
| `new RichText()` | 创建空文档 |
| `paragraph(paragraph)` | 追加段落，返回当前文档 |
| `blankLine()` | 追加空行 |
| `getParagraphs()` | 读取段落列表 |
| `new Paragraph()` | 创建段落 |
| `alignment(value)` | 设置 LEFT、MIDDLE 或 RIGHT |
| `add(elem)` | 追加元素 |
| `getElems()` | 读取元素列表 |

## 元素

Paragraph 提供与 Elem 工厂对应的链式方法

| Elem 工厂 / Paragraph 方法 | 参数说明 |
| --- | --- |
| `text(text, styles...)` | 文本，样式为 BOLD、ITALIC、UNDERLINE，可组合 |
| `image(url)` | 图片地址 |
| `image(url, widthPercent)` | 图片及相对宽度，必须为有限正数，原样写入 width_percent |
| `video(url)` | 视频地址 |
| `url(url, description)` | 链接与显示文字 |
| `atUser(userId, userName)` | 频道用户 ID 与显示名称 |

`Elem.getType()` 读取平台元素编号。提及使用频道用户 ID，不使用群成员 OpenID

## 解析与序列化

`RichText.fromJson(String)` 接受 RichText JSON 文本；`fromJson(JsonNode)` 同时接受对象节点和内含 JSON 的字符串节点，适用于事件中双重编码的 content/title

`Paragraph.fromJson(JsonNode)` 与 `Elem.fromJson(JsonNode)` 解析局部结构。解析保留未知字段与元素，重新序列化不会主动删除这些内容

无效 JSON、缺少 paragraphs 数组或结构类型错误会抛出 IllegalArgumentException，不返回 null；String 入口传 null 会抛出 NullPointerException。读取段落和元素得到列表副本，但其中的对象仍是原对象；追加段落后对该段落的修改会反映到文档中

::: important 对象结构与 JSON 字符串
三个层级的 `toObject()` 返回可序列化 Map；RichText 的 `toJson()` 和 `toString()` 返回 JSON 字符串。普通 MessageBody 对象字段应使用对象结构，GuildThread 的 content 则按平台约定保存字符串
:::

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### RichText

```java
public static RichText fromJson(String json);
public static RichText fromJson(JsonNode node);
public List<Paragraph> getParagraphs();
public RichText paragraph(Paragraph paragraph);
public RichText blankLine();
public Map<String, Object> toObject();
public String toJson();
public String toString();
```

### Paragraph

```java
public Paragraph();
public static Paragraph fromJson(JsonNode node);
public List<Elem> getElems();
public Paragraph add(Elem elem);
public Paragraph alignment(Alignment alignment);
public Paragraph text(String text, TextStyle... styles);
public Paragraph image(String url);
public Paragraph image(String url, double widthPercent);
public Paragraph video(String url);
public Paragraph url(String url, String description);
public Paragraph atUser(String userId, String userName);
public Map<String, Object> toObject();
```

### Elem

```java
public static Elem fromJson(JsonNode node);
public int getType();
public static Elem text(String text, TextStyle... styles);
public static Elem image(String url);
public static Elem image(String url, double widthPercent);
public static Elem video(String url);
public static Elem url(String url, String description);
public static Elem atUser(String userId, String userName);
public Map<String, Object> toObject();
```
