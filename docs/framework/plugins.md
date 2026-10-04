# 插件开发

[文档目录](../README.md) · [事件](events.md) · [指令](commands.md)

## 文件结构

宿主启动时扫描运行目录 `plugins/` 下的 JAR，读取 JAR 根目录的 `plugin.yml`

```text
plugin-project/
├── build.gradle.kts
├── libs/
│   └── atrimeow-sdk.jar
└── src/main/
    ├── java/example/HelloPlugin.java
    └── resources/plugin.yml
```

插件数据目录为 `plugins/<插件名>/`，通过 `PluginContext.getDataDirectory()` 获取

## 编译依赖

使用当前宿主构建生成的 `AtriMeow-<version>-plain.jar` 作为 SDK，复制到插件的 `libs/atrimeow-sdk.jar`

最小 Gradle 配置：

```kotlin
plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    compileOnly(files("libs/atrimeow-sdk.jar"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}
```

宿主 SDK 只参与编译，不打入插件 JAR。使用 Maven 时同样将宿主作为编译期依赖处理。插件自己的第三方依赖按需打包，存在版本冲突时进行包名重定位

插件和宿主使用相同 Java 版本。宿主修改类型或方法签名后，应更新 SDK 并重新编译插件；例如事件的用户返回类型变化会导致旧二进制出现 `NoSuchMethodError`

## plugin.yml

```yaml
name: hello
version: "1.0.0"
main: example.HelloPlugin
api-version: 1
depend: []
commands:
  hello:
    description: 发送问候
    usage: /<command>
    aliases:
      - hi
    prefixless-aliases:
      - 插件问候
    permission: hello.use
    permission-message: 权限不足
```

| 字段 | 说明 |
| --- | --- |
| `name` | 小写字母开头，由小写字母、数字、下划线、连字符组成，最长 64 字符 |
| `version` | 插件版本 |
| `main` | 继承 `AtriPlugin` 的完整类名，提供公共无参构造 |
| `api-version` | 当前为 `1` |
| `depend` | 必需插件名称列表 |
| `commands` | 插件文本指令声明 |

## 主类

```java
package example;

import top.yzljc.atribot.plugin.AtriPlugin;

public final class HelloPlugin extends AtriPlugin {
    @Override
    public void onEnable() {
        getCommand("hello").setExecutor((sender, command, label, args) -> {
            sender.sendMessage("Hello");
            return true;
        });
    }
}
```

未指定命令执行器时，调用插件自身的 `onCommand(...)`。指令权限在进入执行器前检查；返回 `false` 且配置了用法时发送用法提示，`<command>` 替换为实际调用名称

## 生命周期与依赖

| 阶段 | 用途 |
| --- | --- |
| 构造 | 创建对象，不访问插件上下文 |
| `onLoad()` | 上下文已可用，加载资源 |
| `onEnable()` | 注册监听器、设置命令执行器、启动任务 |
| `onDisable()` | 停止业务并释放自行持有的资源 |

加载按依赖关系排序，停用按相反顺序执行。必需依赖缺失、失败或存在无效依赖关系时，相应插件不能正常启用

插件在核心初始化之后、平台连接启动之前启用。加载器不提供热重载，替换 JAR 后重启宿主

## 上下文

| 方法 | 用途 |
| --- | --- |
| `getBot()` | 宿主实例 |
| `getDescription()` | 插件描述 |
| `getLogger()` | 带插件名称的 `System.Logger` |
| `getDataDirectory()` | 插件数据目录 |
| `saveDefaultConfig()` | 从插件资源复制默认 `config.yml`，已有文件不覆盖 |
| `registerEvents(listener)` | 注册事件，停用时注销 |
| `getScheduler()` | 插件专用单线程调度器，首次使用时创建 |
| `manage(resource)` | 登记 `AutoCloseable`，停用时逆序关闭 |
| `getConnection()` | 从宿主连接池借用数据库连接 |
| `getPlugin(name)` | 查询其他插件 |
| `getLoginService()` | 取得共享临时登录服务 |

数据库连接使用 try-with-resources 归还。不要关闭宿主共享连接池、事件管理器或登录服务

插件调度器的任务串行执行。耗时任务需要自行管理执行器和容量；可通过 `manage(...)` 登记关闭。周期任务抛出未处理异常时，后续执行受 JDK 调度器规则影响

## 指令名称与平台

插件命令始终可通过 `插件名:命令名` 定位。短名称和别名发生冲突时，核心命令优先，插件应保留带命名空间的用法

插件命令挂接当前文本分发，不自动注册为 Discord Slash Command。是否支持某个平台还取决于执行器使用的发送能力

`prefixless-aliases` 提供跨文本场景的无前缀入口，支持中文触发词，匹配规则见 [无前缀触发词](commands.md#无前缀触发词)。该入口仍检查插件指令权限，卸载时移除映射；触发词与其他指令冲突时拒绝注册

## 隔离范围

插件使用独立类加载器，宿主类优先加载。该机制解决类加载和生命周期组织，不是权限沙箱。插件与宿主处于同一进程，具有进程可用的文件、网络和数据库访问能力

实现入口：[PluginManager](../../src/main/java/top/yzljc/atribot/plugin/PluginManager.java)、[PluginContext](../../src/main/java/top/yzljc/atribot/plugin/PluginContext.java)、[AtriPlugin](../../src/main/java/top/yzljc/atribot/plugin/AtriPlugin.java)
