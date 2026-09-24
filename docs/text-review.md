# 主动推送文本审查

`TextReviewService` 是独立的同步调用接口。调用位置由业务选择，不会自动接入发送事件，也不复用 `chat/official/moderation`。

```java
import top.yzljc.atribot.service.textreview.TextReviewService;

import java.util.List;

String reviewed = TextReviewService.review(content, List.of("example.com", "www.example.com"));
```

每次调用对原文执行本地词库匹配和 AI 审查，合并命中位置后替换。无命中时原样返回，空格、换行、大小写及其他未命中字符均保留。违规片段按原文字符数替换为等量的 `*`，例如三个字符替换为 `***`。字符数按 Unicode 码点计算，单个补充平面字符（如 `🌈`）计一个；长度依据原始片段，不依据词库匹配时的标准化结果。AI 只返回原文片段及其出现次数，不负责重写全文。

需要记录具体修改时，调用 `TextReviewService.reviewDetailed(content, allowedDomains)`，通过 `content()` 获取处理后的全文，通过 `replacements()` 获取每个实际替换片段的原文、替换文本及原文位置。位置为从 0 开始的 UTF-16 下标区间 `[start, end)`，未修改片段和白名单链接不包含在记录中。自建实例可使用 `sanitizeDetailed(content, allowedDomains)`。

`MinecraftNews` 在生成图片前审查标题、作者和摘要，使用空白名单。审核日志只输出发生修改的字段、原文位置及片段前后对比，不输出完整新闻；换行等控制字符按 JSON 字符串转义。无修改时仅记录“未处理任何片段”。

## 链接白名单

- 只按解析后的完整域名匹配，不区分大小写，不做后缀匹配。`example.com` 不会放行 `www.example.com`、`example.com.evil.com` 或 `example.com@evil.com`。
- 白名单可以传域名或 HTTP(S) URL；协议、端口和路径不参与域名比较。国际化域名按 IDN 标准转换后比较。
- 白名单 URL 本身原样保留；链接文字及周围内容仍接受本地和 AI 审查。
- 非白名单链接整段按原文 Unicode 码点数替换为等量的 `*`。支持 HTTP(S)、协议相对链接、常见裸域名及 IPv4 地址；其他识别到的协议链接不予放行。保留 Markdown 链接外层格式和句末标点。
- 不传白名单、传 `null` 或空集合，均屏蔽全部识别到的链接。格式无效的白名单项抛出 `IllegalArgumentException`。
- 不访问链接、不跟随重定向，也不检查白名单站点的目标页面内容。

```java
String reviewed = TextReviewService.review(content);
```

## 词库、模型与异常

默认入口仅从进程工作目录读取以下文件，部署时需自行准备：

```text
data/text-review/words.txt
data/text-review/prompt.txt
```

代码仓库和 JAR 均不提供审核词库及提示词，不会从 classpath 回退读取或自动生成。`data/` 已被 Git 忽略；原资源路径 `src/main/resources/text-review/` 也已忽略，并从资源打包中排除。文件首次使用时一次读入内存，修改后重启生效。

词库使用 UTF-8，一行一个词，忽略空行及 `#` 开头的行。匹配会统一全半角和大小写，忽略空白及零宽格式字符，并将命中位置映射回原文。词库是无语境的硬规则，可能产生误判；需要按实际推送内容增删。

提示词使用 UTF-8，内容不能为空。需要求 AI 仅返回 `{"violations":[]}`，或包含逐字原文片段及其第几次不重叠出现的结果，例如 `{"violations":[{"text":"需要屏蔽的原文片段","occurrence":1}]}`。具体审核规则在本地维护。

默认入口使用项目的 `ai.default` 模型配置及其 `extraBody`，强制覆盖模型、消息、非流式模式及单结果数量。AI 请求超时为 10 秒，每次非空白文本调用都会等待本次审核完成。不会降级调用旧的审核 API。

本地规则文件缺失或不可读时抛出 `TextReviewException`，错误信息包含待检查的绝对路径。AI 请求失败、超时、输出截断、返回无效 JSON 或无法在原文定位的片段时，同样抛出 `TextReviewException`，不返回未经完整审核的原文。由调用方决定跳过本次推送或重试。空白文本原样返回，不调用 AI；`null` 文本抛出 `NullPointerException`。

需要自定义规则路径、模型或超时时，可调用 `TextReviewService.create(wordsFile, promptFile, properties, timeout)` 创建实例，再调用实例的 `sanitize(content, allowedDomains)`；新实例会重新读取规则文件。
