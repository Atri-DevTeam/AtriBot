# AI 与文本审查

[文档目录](../README.md) · [运行时服务](../framework/runtime.md)

## 配置

AI 配置按提供方分组：`default`、`plan_1`、`plan_2`，对应 `AiProvider` 枚举

```yaml
ai:
  default:
    api-key: "替换为 API Key"
    base-url: "https://ai.example.com/v1/chat/completions"
    model: "模型名称"
    timeout: 30000
```

`base-url` 填完整请求地址，`timeout` 单位为毫秒。除 `api-key`、`base-url`、`model`、`timeout` 外的配置项作为额外请求字段保存

[AiService](../../src/main/java/top/yzljc/atribot/service/ai/AiService.java) 按 Chat Completions 格式提交 `messages`，读取 `choices[0].message.content`

```java
AiService ai = new AiService(
        Config.getInstance().getAiPropertiesMap(),
        new ObjectMapper()
);
String answer = ai.askWithSystemPrompt(
        AiProvider.DEFAULT,
        "需要处理的文本",
        "按指定格式返回处理结果"
);
```

指定提供方未配置时回退 `DEFAULT`。普通 `ask(...)` 使用内置系统提示词；服务失败返回预定义提示文本，调用方可用 `AiService.isValidResponse(...)` 识别

配置与实现入口：[Config](../../src/main/java/top/yzljc/atribot/configuration/Config.java)、[AiProperties](../../src/main/java/top/yzljc/atribot/service/ai/AiProperties.java)、[AiProvider](../../src/main/java/top/yzljc/atribot/service/ai/AiProvider.java)

## 文本审查服务

[TextReviewService](../../src/main/java/top/yzljc/atribot/service/textreview/TextReviewService.java) 是按需调用的文本处理服务，结合本地词库、链接规则和 AI 结果生成替换文本

```java
String reviewed = TextReviewService.review(
        content,
        List.of("example.com")
);
TextReviewResult result = TextReviewService.reviewDetailed(content);
```

`reviewDetailed` 返回处理后的 `content` 与替换范围 `replacements`。它不会自动发送或撤回消息，也不会自动审查所有入站事件

### 资源

| 文件 | 内容 |
| --- | --- |
| `data/text-review/words.txt` | UTF-8 本地词库 |
| `data/text-review/prompt.txt` | UTF-8 AI 审查提示词 |

默认实例首次使用时加载并缓存资源，无 JAR 内置回退，修改后重启。默认调用 `ai.default`，审查请求超时为 10 秒

需要独立资源或超时设置时，使用 `TextReviewService.create(wordsFile, promptFile, properties, timeout)` 创建实例

### 处理规则

- 域名白名单为精确匹配，不自动放行子域名；空列表拦截全部链接
- 只检查链接文本，不访问目标 URL
- 遮盖按 Unicode 码点生成星号；替换范围使用 Java 字符串的 UTF-16 起止偏移
- 配置、调用或返回结果无效时抛出 `TextReviewException`，不以原文作为审查成功结果
- 不合法的域名白名单属于参数错误，抛出 `IllegalArgumentException`

这里的域名规则与群管理中的 URL 正则白名单不同。群管理的权限、提醒与页面可编辑范围见 [WebUI 与 Miniapp](../web.md)
