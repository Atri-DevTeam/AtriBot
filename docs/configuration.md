# 配置

[文档目录](README.md) · [部署](getting-started.md)

运行目录使用两个配置文件，首次运行分别从 JAR 内的默认模板生成，已有文件不覆盖：

| 文件 | 加载器 | 模板 |
| --- | --- | --- |
| `atri-core.yml` | [CoreConfig](../atri-core/src/main/java/top/yzljc/atricore/core/CoreConfig.java) | [框架配置](../atri-core/src/main/resources/atri-core.yml) |
| `config.yml` | [Config](../src/main/java/top/yzljc/atribot/configuration/Config.java) | [业务配置](../src/main/resources/config.yml) |

框架只读取 `atri-core.yml` 中的平台配置，不回退读取旧 `config.yml`。升级现有部署时，将下表框架字段及 QQ / Napcat 连接参数移到新文件；WebUI 登录密钥和其他业务字段仍保留在 `config.yml`

## 核心字段

| 字段 | 用途 |
| --- | --- |
| `debug-command-suffix` | 指令调试后缀 |
| `env` | 默认 `production`；`dev` 在启动时开启 WebUI |
| `mysql.*` | 数据库连接参数 |
| `api-url` | 部分业务使用的 API 地址 |
| `ugc-api-url` | 图片与新版业务服务根地址，由 OpenAPI 目录解析具体接口 |
| `atribot-key-secret` | 对应业务服务的鉴权密钥 |
| `delivery.oss-dump-base-url` | 默认图片分发中的 OSS 地址配置 |
| `ttf-file-name` | 本地图像绘制使用的字体文件名 |

`env`、`ugc-api-url` 可在配置中显式添加，当前内置模板未列出这两个字段。`ugc-api-url` 的代码默认值为 `http://localhost:1234`，它不是项目自动提供的生图服务

```yaml
env: production

mysql:
  host: "127.0.0.1"
  port: 3306
  database: "atrimeow"
  username: "atrimeow"
  password: "替换为数据库密码"

ugc-api-url: "https://images.example.com"
```

以上为配置片段，应合并进完整模板

## 平台配置

以下字段位于 `atri-core.yml`：

| 配置节点 | 主要字段 |
| --- | --- |
| `qq` | `enabled`、`app-id`、`client-secret`、`api-base-url`、`connection-mode`、`webhook-path` |
| `napcat` | `enabled`、`server-url`、`bot-uin`、`admin-uins` |
| `command-prefix` | 文本指令前缀，默认 `/` |
| `debug-mode` | 框架调试模式 |
| `listen-port` | 共享 Javalin 监听端口，默认 `1234`；修改后重启 |
| `thread` | `max-concurrency`、`queue-capacity`、`schedule-capacity` |

`qq.debug-group-openId`、`qq.webhook-queue-capacity` 及 `napcat.token`、`napcat.debug-group-uin`、`napcat.message-spy-groups`、`napcat.queue-capacity`、`napcat.queue-bytes`、`napcat.workers` 也属于框架配置

`config.yml` 中保留 `qq.official-webui-token`、`qq.official-openId`、`qq.official-username`、`qq.super_admin_id`、`napcat.recall-ignore-user` 及 `tencent-channel`。这些字段用于管理页面和运行服务业务。WebUI、Miniapp、QQ Webhook 和 Napcat 上报共用 `atri-core.yml` 的 `listen-port`，旧 `config.yml` 中的端口字段不再读取

平台接入详见 [平台与消息](framework/platforms.md)。`tencent-channel` 使用外部 CLI 登录第二账号，单独说明见 [QQ 频道第二账号](integrations/tencent-channel.md)

Discord 与 KOOK 已移至独立插件，主配置不再读取 `discord`、`kook` 节点。分别使用 `plugins/platform-discord/config.yml` 和 `plugins/platform-kook/config.yml`，迁移步骤见 [Discord](../platform-discord/README.md)、[KOOK](../platform-kook/README.md)

## 页面与服务

| 配置 | 用途 | 文档 |
| --- | --- | --- |
| `qq.official-webui-token` | WebUI 登录密钥 | [WebUI 与 Miniapp](web.md) |
| `miniapp.enabled`、`miniapp.base-url` | Miniapp 开关与外部访问地址 | [WebUI 与 Miniapp](web.md) |
| `ai.<provider>.*` | AI 模型、地址、密钥与超时 | [AI 与文本审查](integrations/ai.md) |
| `censor.*`、`minecraft-moderation.*` | 外部审查服务 | [功能索引](features.md) |
| `image-source.*` | 图片投稿与图源 | [功能索引](features.md) |
| `function.*` | 推送及业务服务 | [功能索引](features.md) |
| `sound.*` | Minecraft 音效资源与答题时间 | [功能索引](features.md) |
| `email.*`、`verify.*` | 邮件监听、Minecraft 验证 | [功能索引](features.md) |

## 修改生效范围

`CoreConfig.getInstance().reload()` 重载框架配置，解析失败时保留旧配置对象。`Config.reload()` 只重载业务配置。已经创建的客户端、监听端口、连接池和静态字段不会因此全部重建。平台开关、凭据、连接方式、回调路径和线程队列参数修改后应重启

WebUI 的设置接口只开放选定字段，按字段归属写入对应配置文件并重载，不改变现有页面布局。它不是完整配置文件编辑器

机器人设置中的「默认加群欢迎」分别配置 `USER`、`ADMIN`、`OWNER` 三种机器人权限身份的正文和按钮，保存后立即用于后续入群事件。群自定义欢迎优先，群的 `member_add_welcome` 开关仍须开启

配置保存在 `data/join-welcome-defaults.json`，首次使用从 JAR 内同名模板初始化。WebUI 的「恢复初始模板」只替换当前身份的配置。直接修改运行目录文件后需要重启进程重新加载

## 配置、资源与数据

| 位置 | 性质 |
| --- | --- |
| 运行目录 `atri-core.yml` | 平台与框架配置 |
| 运行目录 `config.yml` | 运行服务业务配置 |
| JAR 内 `atribot.yml` | 核心指令声明，构建资源 |
| 运行目录 `groupconfig.json` | Napcat 群功能配置 |
| `data/` | 功能设置、群管理规则、提醒、缓存等运行数据 |
| MySQL | 用户、群、消息记录、账号及业务持久化 |
| `plugins/<插件名>/` | 插件数据 |

常用文件路径集中在 [Properties](../src/main/java/top/yzljc/atribot/configuration/Properties.java)。不同模块有各自的存储方式，不存在一个覆盖全部数据的配置文件
