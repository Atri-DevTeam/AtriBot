# 快速开始

[文档目录](../README.md)

本文创建一个运行服务，启动 HTTP 健康检查接口，并通过配置启用机器人平台

## 环境要求

- JDK 25
- 编译和运行时启用 `--enable-preview`
- 使用源码仓库提供的 Gradle Wrapper 构建框架

## 1. 安装依赖

在框架仓库根目录发布到本地 Maven 仓库

```sh
./gradlew :atri-core:publishToMavenLocal
```

在运行服务中创建 `settings.gradle.kts`

```kotlin
rootProject.name = "bot-example"
```

创建 `build.gradle.kts`，依赖版本应与本地发布版本一致

```kotlin
plugins {
    application
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("top.yzljc.atricore:atri-core:0.1.0-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

application {
    mainClass.set("example.BotApplication")
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}
```

同一 Gradle 工程内可使用 `implementation(project(":atri-core"))` 直接引用源码模块

正式版本发布至 Maven Central 后，可将依赖版本替换为已发布版本，仅保留 `mavenCentral()`，使用方无需配置发布凭据

## 2. 编写入口

创建 `src/main/java/example/BotApplication.java`

```java
package example;

import top.yzljc.atricore.AtriProvider;
import top.yzljc.atricore.service.runtime.ThreadManager;

public final class BotApplication extends AtriProvider {
    public static void main(String[] args) {
        var application = new BotApplication();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                application.close();
            } finally {
                ThreadManager.shutdown();
            }
        }, "bot-shutdown"));

        application.initialize();
        application.getServer().get("/health", ctx -> ctx.result("ok"));
        application.start();
    }
}
```

`AtriProvider` 是抽象基类，基础服务已有默认实现，业务可按需覆盖扩展方法

`initialize()` 准备框架服务，`start()` 开始监听 HTTP 并启动已启用的平台，`close()` 释放框架资源

## 3. 配置平台

首次初始化会在工作目录生成 `atri-core.yml`，默认端口为 `1234`，两个平台默认关闭

QQ Webhook 配置示例，修改对应字段后重启运行服务

```yaml
qq:
  enabled: true
  app-id: "你的 AppID"
  client-secret: "你的 AppSecret"
  connection-mode: "webhook"
  webhook-path: "/qq/webhook"
```

在 QQ 开放平台将回调地址设置为运行服务的公网地址加 `/qq/webhook`

Napcat 配置示例

```yaml
napcat:
  enabled: true
  server-url: "http://127.0.0.1:3000"
  token: "你的访问 Token"
  bot-uin: "机器人 QQ 号码"
```

在 Napcat 中启用 HTTP API，并将 HTTP 事件上报地址设置为运行服务地址的 `/` 路径，例如 `http://127.0.0.1:1234/`

以上为配置片段，全部字段见 [配置](configuration.md)

## 4. 启动

使用 Gradle 的 `run` 任务启动运行服务

```sh
gradle run
```

已有 Gradle Wrapper 时可使用 `./gradlew run`，Windows 使用 `gradlew.bat run`

访问 `http://127.0.0.1:1234/health`，响应内容为 `ok`

## 5. 注册业务

在 `initialize()` 之后、`start()` 之前注册指令、监听器和业务路由

| 功能 | 文档 |
| --- | --- |
| 注册文本指令 | [指令](commands.md) |
| 监听平台事件 | [事件](events.md) |
| 注册周期任务 | [任务调度](scheduling.md) |
| 接入账号权限和记录 | [Provider](provider.md#业务扩展) |

静态文件、CORS 等 Javalin 创建选项通过 `initialize(config -> { ... })` 设置，业务路由通过 `getServer()` 注册，与平台回调共用端口

## 关闭服务

示例通过 JVM 关闭钩子释放资源，已有退出流程的运行服务可在退出流程中调用 `close()`

Provider 关闭平台连接、HTTP 服务、框架回调队列、`Scheduler` 和 `TaskScheduler`，数据库、业务监听器及自建客户端由运行服务关闭，最后调用 `ThreadManager.shutdown()` 关闭进程共享线程

一个进程使用一个 Provider，关闭后的实例不能重新启动
