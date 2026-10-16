# Ark 卡片

[API 目录](../../README.md) · 包 `top.yzljc.atricore.chat.official.ark`

Ark 使用平台模板 ID 和键值内容，当前封装提供模板 23、24、37

::: danger 被动消息使用条件
Ark 被动消息需要私域机器人才能使用，模板 23、24、37 均适用。下方被动回复示例及同步、异步 `replyMessage` 重载均受此限制
:::

## 列表模板 23

```java
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.ark.Ark;
import top.yzljc.atricore.chat.official.ark.Ark23;
import java.util.List;

Ark card = Ark.ark23("查询列表", "收到查询结果", List.of(
        Ark23.Item.text("当前状态正常"),
        Ark23.Item.link("详情", "https://example.com/detail")));
String sentId = GroupChat.replyMessage(groupOpenId, RT.message(messageId), card);
```

## 模板参数

| 工厂 | 参数顺序 |
| --- | --- |
| `Ark.ark23` | `description` 描述、`prompt` 提示、`items` 列表 |
| `Ark.ark24` | `description` 描述、`prompt` 提示、`title` 标题、`metaDescription` 正文、`picUrl` 图片、`jumpUrl` 跳转、`subTitle` 副标题 |
| `Ark.ark37` | `prompt` 提示、`title` 标题、`subTitle` 副标题、`picUrl` 图片、`jumpUrl` 跳转 |

所有文本参数为 String，使用非 null 值。`Ark23.Item.text(desc)` 创建文本项，`Item.link(desc, link)` 创建链接项且要求非空链接；也可使用 `new Ark23.Item(desc, link)`

## 组合与发送

`Ark23`、`Ark24`、`Ark37` 都实现 `Ark.ArkContent`，可通过 `new Ark(content)` 包装。`getType()` 返回模板类型，`getContent()` 读取内容，`toObject()` 转换为带 `template_id` 和 `kv` 的请求对象

群聊和 C2C 提供主动发送与被动回复的 Ark 重载，异步类提供相同参数。模板使用资格和可发送场景由平台授权决定，创建对象成功不代表远端允许发送

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### Ark

```java
public static Ark ark23(String description, String prompt, List<Ark23.Item> items);
public static Ark ark24(String description, String prompt, String title, String metaDescription, String picUrl, String jumpUrl, String subTitle);
public static Ark ark37(String prompt, String title, String subTitle, String picUrl, String jumpUrl);
public Type getType();
public Map<String, Object> toObject();
```

### Ark23

```java
public Ark.Type getType();
public List<Map<String, Object>> toObject();
public record Item(String description, String link);
```

### Ark24

```java
public Ark.Type getType();
public List<Map<String, Object>> toObject();
```

### Ark37

```java
public Ark.Type getType();
public List<Map<String, Object>> toObject();
```
