# 开发与构建

[文档目录](README.md)

## 源码布局

| 目录 | 职责 |
| --- | --- |
| `src/main/java/top/yzljc/atribot/platform` | 平台连接、协议解析、用户和消息类型 |
| `src/main/java/top/yzljc/atribot/chat` | 平台消息 API 与内容组件 |
| `src/main/java/top/yzljc/atribot/event` | 事件系统与具体事件 |
| `src/main/java/top/yzljc/atribot/command` | 命令定义、发送者、执行器与分发 |
| `src/main/java/top/yzljc/atribot/auth` | 平台权限、账号绑定、登录验证 |
| `src/main/java/top/yzljc/atribot/plugin` | 插件加载与上下文 |
| `src/main/java/top/yzljc/atribot/service` | 请求、AI、调度、线程与公共服务 |
| `src/main/java/top/yzljc/atribot/database` | 连接池、Repository 与 DTO |
| `src/main/java/top/yzljc/atribot/function` | 业务指令、事件监听与定时任务 |
| `src/main/java/top/yzljc/atribot/webui`、`miniapp` | 两套页面的服务端接口 |
| `src/main/java/top/yzljc/sakuraba_ema` | 腾讯频道第二账号 CLI 封装 |
| `webui`、`miniapp` | 前端源码 |
| `src/main/resources` | 配置模板、指令声明、语言和静态资源 |

## 后端

使用 JDK 25 导入 Gradle 项目。构建配置已为编译、测试和 JavaExec 启用预览特性；IDE 直接运行主类时同样需要 `--enable-preview`

```sh
./gradlew test
./gradlew build
```

主类为 `top.yzljc.atribot.Atri`。启动会初始化数据库和启用的外部连接，运行配置按进程工作目录读取

筛选测试可使用：

```sh
./gradlew test --tests 'top.yzljc.atribot.auth.*'
```

## 前端

两个前端分别安装依赖、启动开发服务器：

```sh
npm --prefix webui ci
npm --prefix webui run dev
```

```sh
npm --prefix miniapp ci
npm --prefix miniapp run dev
```

| 项目 | 默认开发端口 | 生产路径 | 构建输出 |
| --- | --- | --- | --- |
| WebUI | Vite 默认端口 | `/webui/` | `src/main/resources/official-webui` |
| Miniapp | `5174` | `/atrimeow/profile/` | `src/main/resources/miniapp` |

在对应前端目录的 `.env.local` 中设置 `VITE_API_TARGET=http://127.0.0.1:1234` 可指定开发代理。开发代理仍使用后端认证规则

WebUI 的共享样式、请求和消息渲染位于 `webui/src/shared`，业务页面位于 `webui/src/views`。Miniapp 的群管理组件位于 `miniapp/src/components`

前端检查：

```sh
npm --prefix webui run build
npm --prefix miniapp test
npm --prefix miniapp run build
```

Miniapp 构建包含 TypeScript 检查，浏览器测试入口为 `npm --prefix miniapp run test:browser`，其环境要求见 [Playwright 配置](../miniapp/playwright.config.ts)

## 扩展入口

- 添加核心命令：声明 `atribot.yml`，编写执行器，在 `Atri.onEnable()` 注册
- 添加事件处理：实现 `Listener`，注册到 `EventManager`
- 添加平台：实现平台协议、用户和消息类型、发送者及事件入口，保留平台专有字段
- 添加独立功能：使用 [插件 API](framework/plugins.md)，避免将宿主类打入插件 JAR

具体约束分别见 [事件](framework/events.md)、[指令](framework/commands.md) 和 [平台与消息](framework/platforms.md)

## 文档维护

文档描述当前实现。接口签名、配置项和生命周期发生变化时，同步修改对应页面及源码链接；业务功能只维护入口和必要配置。新增文档在 [文档目录](README.md) 注册
