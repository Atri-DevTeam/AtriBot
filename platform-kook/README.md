# KOOK 平台插件

提供 KOOK Webhook、消息与按钮事件、文本指令、卡片和资源上传。停用插件时关闭事件队列并移除回调处理器

## 构建与安装

在仓库根目录使用 JDK 25 构建：

```sh
./gradlew :platform-kook:jar
```

将 `build/libs/` 下的插件 JAR 复制到宿主运行目录的 `plugins/`，本插件不包含宿主类及第三方依赖。宿主需要使用同版本构建

创建 `plugins/platform-kook/config.yml`，内容参考 [默认配置](src/main/resources/config.yml)。也可先启动一次生成模板，填写后重启；缺少凭据时插件不会启用

## 从内置适配迁移

1. 将原主配置 `kook` 节点下的凭据和参数复制到插件配置的顶层，去掉 `kook:` 和 `enabled`
2. 设置 `webhook-path: /webhook`
3. 将平台 Callback URL 改为 `https://你的域名/plugins/platform-kook/webhook`，同步检查反向代理转发规则
4. 安装插件并重启宿主

原 `/kook/webhook` 不再注册，主配置中的 `kook` 节点不再读取。不安装插件时，主项目不会建立 KOOK 连接或注册 KOOK 回调

## 指令与扩展

保留 16 个主指令及原有别名，声明见 [plugin.yml](src/main/resources/plugin.yml)，具体支持范围见 [平台文档](../docs/framework/platforms.md#kook)

动态图片使用 API 获取，随后上传至 KOOK，不使用 COS。指令执行的控制台日志和调试群记录由插件注册

专有类保留原包名。其他插件使用 KOOK 类型时，将本插件 JAR 加入编译期依赖，并声明 `depend: [platform-kook]`
