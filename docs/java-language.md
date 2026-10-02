# Java 语言组件

当前仅提供独立组件，尚未接入现有配置、指令或消息发送流程。原有回复内容保持原样，`atribot.yml` 的描述和用法不参与语言文件管理。

## 基本使用

```java
import top.yzljc.atribot.i18n.I18N;

import java.util.Map;

String message = I18N.text("common.no_permission");
String cooldown = I18N.text("common.cooldown", Map.of("seconds", 30));
String buttonLabel = I18N.text("button.next_page");
```

默认语言为 `zh_cn`。组件只返回字符串，不发送消息，不读取数据库，也不改变任何业务状态。调用方将返回值交给现有消息或按钮 API 即可。

## 设置默认语言与指定语言

```java
boolean changed = I18N.setDefaultLanguage("zh_cn");
String current = I18N.getDefaultLanguage();

// 查询指定语言，不影响其他请求使用的默认语言。
String message = I18N.textFor("en_us", "common.no_permission");
String cooldown = I18N.textFor("en_us", "common.cooldown", Map.of("seconds", 30));
```

`setDefaultLanguage` 只修改进程内的默认值，不写入配置文件、不调用 `Locale.setDefault()`。未来接入全局配置时可在启动阶段调用；群聊或用户的语言偏好使用 `textFor` 传入，不要逐条消息切换全局默认值。

缺失、空对象或格式错误的语言文件不能被设为默认语言，方法返回 `false` 并保留原值。查询时缺少目标语言或文案键会回退 `zh_cn`；中文也缺少该键时返回 `[key]`，方便定位漏迁移的文案。语言标识和语义键格式无效属于调用错误，抛出 `IllegalArgumentException`。

## 语言文件

文件位于 `src/main/resources/lang/<语言标识>.json`，使用 UTF-8 编码和扁平 JSON 对象。例如后续添加 `en_us.json`：

```json
{
  "common.no_permission": "You do not have permission to perform this action.",
  "common.cooldown": "Please try again in {seconds} seconds.",
  "button.next_page": "Next page"
}
```

语义键使用小写字母、数字、下划线及点，每段以字母开头；同一键在不同语言文件中表达同一含义。所有值必须是字符串，重复键、嵌套对象、空文件以及多余 JSON 内容都会使整份文件加载失败。空字符串是有效文案，不会触发回退。

语言标识兼容 `zh_cn`、`zh-CN` 等写法，统一转为小写下划线形式查找资源。语言文件首次使用时加载为只读缓存，后续查询不重复读文件。文件缺失或解析失败的结果也会缓存；修改资源后需重新构建并重启，不提供热重载。

## 参数和展示格式

参数使用 `{name}`，名称以英文字母开头，后续可包含数字和下划线。缺少或值为 `null` 的参数保留原占位符，并记录警告；额外参数忽略。替换仅执行一遍，参数中的 `$`、反斜杠或其他占位符不会被当成替换指令再次展开。

语言文件应保存完整句子，以便翻译时调整语序。换行使用 JSON 的 `\n`，Markdown 标记可以直接放在字符串中。参数替换不负责 Markdown、HTML 或 QQ 标签转义；插入用户输入时，由调用方按照消息格式转义。复杂数字、日期和复数形式暂不自动处理，可先格式化再作为参数传入。

首次迁移可从通用提示和按钮标题开始。按钮 ID、指令参数、权限节点、数据库字段、日志和 AI 提示词不随这些文案一起迁移。
