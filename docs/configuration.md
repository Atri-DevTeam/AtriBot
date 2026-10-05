# 配置

[文档目录](README.md) · [部署](getting-started.md)

配置入口为运行目录的 `config.yml`，由 [Config](../src/main/java/top/yzljc/atribot/configuration/Config.java) 加载。完整模板见 [src/main/resources/config.yml](../src/main/resources/config.yml)

## 核心字段

| 字段 | 用途 |
| --- | --- |
| `command-prefix` | 文本指令前缀，默认 `/` |
| `debug-mode` | 调试模式 |
| `debug-command-suffix` | 指令调试后缀 |
| `listen-port` | Javalin 监听端口，默认 `1234` |
| `env` | 默认 `production`；`dev` 在启动时开启 WebUI |
| `mysql.*` | 数据库连接参数 |
| `api-url` | 部分业务使用的 API 地址 |
| `ugc-api-url` | 图片与新版业务服务根地址，由 OpenAPI 目录解析具体接口 |
| `atribot-key-secret` | 对应业务服务的鉴权密钥 |
| `delivery.oss-dump-base-url` | 默认图片分发中的 OSS 地址配置 |
| `ttf-file-name` | 本地图像绘制使用的字体文件名 |

`env`、`ugc-api-url` 可在配置中显式添加，当前内置模板未列出这两个字段。`ugc-api-url` 的代码默认值为 `http://localhost:1234`，它不是项目自动提供的生图服务

```yaml
listen-port: 1234
command-prefix: "/"
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

| 配置节点 | 主要字段 |
| --- | --- |
| `qq` | `enabled`、`app-id`、`client-secret`、`api-base-url`、`connection-mode`、`webhook-path` |
| `napcat` | `enabled`、`server-url`、`bot-uin`、`admin-uins` |
| `discord` | `enabled`、`bot-token`、`api-base-url`、`intents` |
| `kook` | `enabled`、`bot-token`、`verify-token`、`encrypt-key`、`webhook-path`、`admin-ids` |
| `tencent-channel` | `enabled`、`cli-path`、`login-token`、`timeout-seconds` |

平台接入详见 [平台与消息](framework/platforms.md)。`tencent-channel` 使用外部 CLI 登录第二账号，单独说明见 [QQ 频道第二账号](integrations/tencent-channel.md)

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

`Config.reload()` 重新读取配置对象，但已经创建的客户端、监听端口、连接池和静态字段不会因此全部重建。平台凭据、连接方式和基础设施配置修改后应重启

WebUI 的设置接口只开放选定字段，各接口负责自己的刷新行为。它不是完整 `config.yml` 编辑器

机器人设置中的「默认加群欢迎」分别配置 `USER`、`ADMIN`、`OWNER` 三种机器人权限身份的正文和按钮，保存后立即用于后续入群事件。群自定义欢迎优先，群的 `member_add_welcome` 开关仍须开启

配置保存在 `data/join-welcome-defaults.json`，首次使用从 JAR 内同名模板初始化。WebUI 的「恢复初始模板」只替换当前身份的配置。直接修改运行目录文件后需要重启进程重新加载

## 配置、资源与数据

| 位置 | 性质 |
| --- | --- |
| 运行目录 `config.yml` | 部署配置 |
| JAR 内 `atribot.yml` | 核心指令声明，构建资源 |
| 运行目录 `groupconfig.json` | Napcat 群功能配置 |
| `data/` | 功能设置、群管理规则、提醒、缓存等运行数据 |
| MySQL | 用户、群、消息记录、账号及业务持久化 |
| `plugins/<插件名>/` | 插件数据 |

常用文件路径集中在 [Properties](../src/main/java/top/yzljc/atribot/configuration/Properties.java)。不同模块有各自的存储方式，不存在一个覆盖全部数据的配置文件
