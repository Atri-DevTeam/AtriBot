# atri-core

面向 QQ 官方机器人、QQ 频道和 Napcat / OneBot 的 Java 开发框架

提供平台连接、消息收发、事件监听、指令注册、HTTP 服务和任务调度，包名为 `top.yzljc.atricore`

## 开始使用

需要 JDK 25，编译和运行时启用 `--enable-preview`

运行服务继承 `AtriProvider`，初始化后注册业务，再启动平台服务

```java
public final class BotApplication extends AtriProvider {
    public static void main(String[] args) {
        var application = new BotApplication();
        application.initialize();
        application.getServer().get("/health", ctx -> ctx.result("ok"));
        application.start();
    }
}
```

依赖配置、完整入口及退出处理见 [快速开始](docs/getting-started.md)

## 开发文档

[完整文档目录](docs/README.md)

| 分类 | 文档 |
| --- | --- |
| 接入 | [快速开始](docs/getting-started.md)、[配置](docs/configuration.md)、[Provider](docs/provider.md)、[构建与开发](docs/development.md) |
| 框架 | [指令](docs/commands.md)、[事件](docs/events.md)、[用户与消息](docs/messages.md)、[调度](docs/scheduling.md) |
| QQ 消息 | [群聊](docs/qq/messages/group.md)、[C2C](docs/qq/messages/c2c.md)、[频道](docs/qq/messages/guild.md)、[流式](docs/qq/messages/stream.md)、[文件上传](docs/qq/messages/media.md) |
| QQ 组件 | [Markdown](docs/qq/components/markdown.md)、[按钮](docs/qq/components/buttons.md)、[图文卡片](docs/qq/components/cards.md)、[Ark](docs/qq/components/ark.md)、[Embed](docs/qq/components/embed.md) |
| QQ 交互 | [自定义菜单](docs/qq/interaction/menu.md)、[指令面板](docs/qq/interaction/panel.md)、[按钮回调](docs/qq/interaction/callback.md) |
| QQ 群管理 | [禁言](docs/qq/management/mute.md)、[群成员](docs/qq/management/members.md)、[成员黑名单](docs/qq/management/blacklist.md)、[入群申请](docs/qq/management/join-requests.md)、[审核策略](docs/qq/management/join-strategies.md) |
| QQ 帖子与资料 | [帖子](docs/qq/forum/threads.md)、[富文本](docs/qq/forum/rich-text.md)、[机器人与群资料](docs/qq/bot.md) |
| Napcat | [消息](docs/napcat/messages.md)、[消息段](docs/napcat/segments.md)、[转发](docs/napcat/forwarding.md)、[群操作](docs/napcat/groups.md)、[用户与好友](docs/napcat/users.md)、[HTTP 调用](docs/napcat/requests.md) |

## 模块职责

框架管理平台连接、令牌、聊天服务、Javalin 和计划调度器，运行服务通过 Provider 接入账号权限、消息记录及业务策略

QQ 群聊 / C2C 与 QQ 频道分别提供用户、消息和事件类型，保留平台特有接口

数据库、业务功能、管理页面及插件加载由运行服务实现

## 构建

在仓库根目录执行，Windows 使用 `gradlew.bat`

```sh
./gradlew :atri-core:build
./gradlew :atri-core:javadoc
./gradlew :atri-core:publishToMavenLocal
```

Maven 坐标：`top.yzljc.atricore:atri-core:<版本>`，core 独立版本默认 `0.1.0-SNAPSHOT`，通过 `-PcoreVersion=<版本>` 指定

本地联调使用 `publishToMavenLocal`
