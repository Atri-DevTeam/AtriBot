# Discord 平台插件

提供 Discord Gateway、消息与 Slash 事件、结构化指令、Embed 和消息组件。停用插件时关闭连接及重连流程

## 构建与安装

在仓库根目录使用 JDK 25 构建：

```sh
./gradlew :platform-discord:jar
```

将 `build/libs/` 下的插件 JAR 复制到宿主运行目录的 `plugins/`，本插件不包含宿主类及第三方依赖。宿主需要使用同版本构建

创建 `plugins/platform-discord/config.yml`，内容参考 [默认配置](src/main/resources/config.yml)。也可先启动一次生成模板，填写后重启；缺少凭据时插件不会启用

## 从内置适配迁移

将原主配置 `discord` 节点下的 `bot-token`、`api-base-url` 和 `intents` 复制到插件配置的顶层，去掉 `discord:` 和 `enabled`。安装插件后重启宿主

主配置中的 `discord` 节点不再读取，不安装插件时，主项目不会连接 Discord

## 指令与扩展

保留 15 个主指令，声明与参数见 [plugin.yml](src/main/resources/plugin.yml)。连接启动时同步已注册的 Slash 指令；暂不支持加载完成后的动态同步

动态图片使用 API 获取，不使用 COS。专有类保留原包名；其他插件使用 Discord 类型时，将本插件 JAR 加入编译期依赖，并声明 `depend: [platform-discord]`
