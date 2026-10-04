# AtriMeow

基于 Java / Kotlin 的跨平台机器人项目，包含平台适配、事件分发、指令执行、账号与权限、任务调度和插件加载框架

支持 QQ 官方机器人、Napcat / OneBot、Discord 和 KOOK，附带 WebUI 管理后台与 Miniapp 用户页面

## 文档

**[文档目录](docs/README.md)** · **[部署](docs/getting-started.md)** · **[配置](docs/configuration.md)** · **[开发与构建](docs/development.md)**

| 框架 | 内容 |
| --- | --- |
| [架构与生命周期](docs/framework/architecture.md) | 模块职责、启动与关闭顺序 |
| [平台与消息](docs/framework/platforms.md) | 平台接入、User、Message、发送与撤回 |
| [QQ 频道帖子](docs/framework/platforms.md#频道帖子) | RichText、发表与删除、任务 ID 与帖子 ID |
| [事件](docs/framework/events.md) | 监听器、优先级、取消与线程 |
| [指令](docs/framework/commands.md) | 文本指令、无前缀触发词、Slash Command、参数与注册 |
| [账号与权限](docs/framework/accounts.md) | AtriAccount、平台身份、权限检查 |
| [插件开发](docs/framework/plugins.md) | SDK、描述文件、生命周期与资源管理 |
| [运行时服务](docs/framework/runtime.md) | 任务调度、数据库、配置与语言资源 |

## 平台

| 平台 | 接入方式 | 指令入口 |
| --- | --- | --- |
| QQ 官方机器人 | WebSocket / Webhook，群聊、C2C、频道、频道私信 | 文本指令 |
| Napcat / OneBot | HTTP 事件上报与 HTTP API | 文本指令 |
| Discord | Gateway 与 HTTP API | Slash Command |
| KOOK | Webhook 与 HTTP API v3 | 文本指令 |

业务指令的支持范围由各执行器决定，接入平台不代表所有功能均已适配。功能分类见 [功能索引](docs/features.md)

## 管理页面

WebUI 提供聊天管理、机器人设置、群管理和日志查询，访问方式与权限说明见 [WebUI 与 Miniapp](docs/web.md)

QQ 官方群支持按 `USER`、`ADMIN`、`OWNER` 配置默认加群欢迎，入口为「机器人设置 → 默认加群欢迎」，生效规则见 [配置](docs/configuration.md#修改生效范围)

## 构建

后端使用 JDK 25，启用 Java 预览特性。Gradle Wrapper 随仓库提供

```sh
./gradlew build
java --enable-preview -jar build/libs/AtriMeow-3.3.0-SNAPSHOT.jar
```

Windows 使用 `gradlew.bat`。版本号以 [构建配置](build.gradle.kts) 为准；部署前需配置 MySQL 和所用平台的凭据，完整步骤见 [部署](docs/getting-started.md)

前端使用 Node.js 24，通过各自目录中的 `npm ci`、`npm run build` 构建。Gradle 不自动执行前端构建，修改前端后应先生成静态资源，再打包后端

## 许可

[MIT License](LICENSE) © 2026 YZ_Ljc_
