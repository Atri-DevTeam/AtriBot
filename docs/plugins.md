# 轻量插件加载器

插件是附加功能，不替换现有功能。机器人登录、数据库、平台适配、原有指令、审核、推送、任务、配置和 WebUI 均保留原样。

## 安装与生命周期

在机器人工作目录放置插件：

```text
plugins/
  hello-plugin.jar
  hello-plugin/
    config.yml
```

- 仅扫描 `plugins/` 第一层的 `.jar`，启动时加载，增删/更新插件后重启。
- 入口必须是继承 `AtriPlugin` 的 public 类，并具有 public 无参构造方法。
- 宿主现有功能初始化后调用 `onLoad()`、`onEnable()`；这时 QQ/Discord 的主动连接不保证已经建立，构造方法和启用回调不要等待平台登录。
- 依赖先启用；关闭时按相反顺序调用 `onDisable()`。启用过程中失败也会尝试调用停用回调，应允许部分初始化。
- 支持原有 `onDisable()` 流程和 JVM 正常退出（包括现有重启命令的 `System.exit`）；强杀进程、掉电无法保证回调。
- 缺少依赖、依赖环、重名插件、入口错误及普通初始化异常会阻止对应插件启用，不停止原有功能；依赖失败的插件也不会启用。
- 不支持 reload、运行中安装、热卸载、softdepend 或 Bukkit 二进制兼容。

## plugin.yml

JAR 根目录必须包含 `plugin.yml`：

```yaml
name: hello-plugin
version: '1.0.0'
main: example.hello.HelloPlugin
api-version: 1
depend: []
commands:
  hello:
    description: 插件问候
    usage: '/<command> [内容]'
    aliases: [hi]
```

`name` 只能由小写字母、数字、下划线、连字符组成，以字母开头，最长 64 字符，同时作为数据目录名。`depend` 是必须提前启用的插件名列表。

## 单独开发，无需根项目

插件在任意独立目录开发，不需要放进 AtriMeow 仓库，也不需要修改根项目 `settings.gradle.kts`。

1. 在宿主项目执行 `gradlew.bat jar`，取得 `build/libs/*-plain.jar`。
2. 将该 JAR 复制到独立插件项目的 `libs/`，仅作为编译期 SDK。也可使用发布好的同版本宿主 JAR。
3. 独立插件项目使用 JDK 25，并准备下面这些文件。

```text
hello-plugin/
  settings.gradle.kts
  build.gradle.kts
  libs/AtriMeow-3.2.2-Release-plain.jar
  src/main/java/example/hello/HelloPlugin.java
  src/main/resources/plugin.yml
  src/main/resources/config.yml
```

`settings.gradle.kts`：

```kotlin
rootProject.name = "hello-plugin"
```

`build.gradle.kts`：

```kotlin
plugins { java }
group = "example"
version = "1.0.0"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
dependencies { compileOnly(fileTree("libs") { include("*.jar") }) }
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}
```

使用独立项目自己的 Gradle/Wrapper 构建 `jar`，不要依赖宿主的源码、构建目录或 Gradle 子项目。这里的普通 JAR 适用于没有额外运行库的插件；如果引入自己的第三方运行库，需要自行打包进插件 JAR。不得把宿主 SDK 打包进去。需要与宿主不同版本的库时，建议打包并重定位包名，避免父优先加载规则导致冲突。

`HelloPlugin.java`：

```java
package example.hello;

import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupMemberAddEvent;
import top.yzljc.atribot.plugin.AtriPlugin;

/**
 * @Author YZ_Ljc_
 * @ClassName HelloPlugin
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package example.hello
 */
public final class HelloPlugin extends AtriPlugin implements Listener {
    @Override
    public void onEnable() throws Exception {
        getContext().saveDefaultConfig();
        getContext().registerEvents(this);
        getCommand("hello").setExecutor((sender, command, label, args) -> {
            sender.sendMessage("Hello from hello-plugin!");
            return true;
        });
        getContext().getLogger().log(System.Logger.Level.INFO, "插件已启用");
    }

    @EventHandler
    public void onJoin(OfficialGroupMemberAddEvent event) {
        getContext().getLogger().log(System.Logger.Level.INFO, "收到入群事件: " + event.getGroupOpenId());
    }

    @Override
    public void onDisable() {
        getContext().getLogger().log(System.Logger.Level.INFO, "插件已停用");
    }
}
```

`config.yml` 内容由插件自行定义和解析；`saveDefaultConfig()` 仅首次从当前插件 JAR 复制到 `plugins/hello-plugin/config.yml`，不会覆盖已有配置。宿主 `config.yml` 不受影响。

## 可用能力

| 接口 | 用途 |
| --- | --- |
| `getContext().getBot()` | 访问当前宿主实例，不创建第二个机器人 |
| `getContext().getDescription()` | 插件名称、版本、入口及依赖 |
| `getContext().getLogger()` | 使用宿主 Logback/JLine 输出日志，自动加上插件名 |
| `getContext().getDataDirectory()` | 当前插件独立数据目录 |
| `getContext().registerEvents(listener)` | 接入原有事件总线，停用/启用失败时自动注销 |
| `getContext().getScheduler()` | 懒创建的插件专用单线程调度器，停用时取消任务并中断线程 |
| `getContext().manage(closeable)` | 托管插件自建资源，停用时逆序关闭 |
| `getContext().getConnection()` | 从主数据库连接池借用连接，用 try-with-resources 归还，禁止关闭宿主连接池 |
| `getContext().getPlugin("name")` | 获取已启用插件实例，返回 Optional |
| `Atri.getInstance().getPluginManager().getPlugins()` | 只读插件状态及失败原因 |

插件也可以直接调用现有的 `GroupChat`、`TC`、数据库仓储等公开 Java API。直接使用第三方类型的 API 时，独立项目可能需要添加对应的 `compileOnly` 依赖。

`getLogger()` 保持 `System.Logger` 接口，原有插件无需修改或重新编译。日志统一经过宿主控制台和文件日志，控制台示例：

```text
[23:45:00] [Plugin/INFO] [xiamuanan-channel-cli] Mojira 动态已推送到第二 Bot 频道
```

继续使用 `getContext().getLogger().log(System.Logger.Level.INFO, "消息")`。`WARNING` 显示为 `WARN`，异常堆栈及日志等级过滤沿用宿主配置；带参数的重载使用 `{0}`、`{1}` 占位符。

插件专用调度器采用 JDK 标准语义：同一插件内任务串行，周期任务抛异常后会停止后续运行，需要插件自行捕获预期异常。不要在生命周期中执行无限等待；长任务要响应中断。自行创建的线程、连接池等必须在停用时关闭或通过 `manage()` 托管。已经进入执行的事件回调和忽略中断的任务无法被强制撤销。

这版不增加插件前端页面或原任务系统的动态注册，不迁移原功能。

## 插件指令

在 `plugin.yml` 的 `commands` 中声明指令，支持 `description`、`usage`、`aliases`、`permission`、`permission-message`。别名既可以写单个字符串，也可以写列表。指令和别名不包含 `/` 或命名空间，只能包含字母、数字、下划线和连字符，最长 64 字符；不区分大小写。同一插件内部的主名称和别名不能重复。

- 在 `onEnable()` 中调用 `getCommand("hello").setExecutor(...)`；查找范围仅限本插件，未声明时返回 `null`，不会误取宿主或其他插件的指令。
- 也可以直接重写入口类的 `onCommand(sender, command, label, args)`，不设置执行器时默认调用该方法。`setExecutor(null)` 恢复这个默认行为。
- 示例可以通过 `/hello`、`/hi`、`/hello-plugin:hello`、`/hello-plugin:hi` 调用；`/` 以实际宿主指令前缀配置为准。
- 宿主原有指令和别名优先。插件之间短名称冲突时先注册者保留短名称，另一插件使用命名空间调用，不覆盖已有指令。
- 所有声明会在生命周期回调前注册，但只有 `onEnable()` 成功返回后才允许执行。启用失败、停用会撤销主名称、别名和命名空间注册；已开始的调用不能强制撤销。
- 执行器返回 `false` 时显示 `usage`，其中 `<command>` 替换为用户实际输入的指令或别名。
- 填写 `permission` 后，执行前使用现有 `CommandSender.hasPermission(String)` 检查；未填写则不附加权限限制。这里不新增 Bukkit 权限树，具体判断沿用平台已有实现。
- 继续经过现有 `UserRunCommandEvent` 和群指令停用检查。插件停用规则使用 `插件名:主指令名` 作为独立键，不会和同名宿主指令共用规则。
- 原有 `CommandManager.reload()` 仅重建宿主指令，保留插件指令及执行器；这不是插件热重载。

本次只接入已有文本指令分发（QQ 群/私聊/频道及 Napcat 群等既有入口），不新增平台入口、不自动修改原 `/help` 文案或 WebUI 指令列表，也不自动发布 Discord Slash Commands。插件入口和业务仍然独立开发。

## 插件间调用

在调用方 `plugin.yml` 中声明 `depend: [other-plugin]`，编译时把对方 API/JAR 作为 `compileOnly` 依赖，不重复打包对方类。运行时通过 `getPlugin("other-plugin")` 获取实例，再按对方公开 API 调用。类加载器只会查找宿主、当前 JAR 及声明依赖的插件，不扫描所有插件碰运气找类。停用调用方时，其依赖仍处于启用状态。

## 边界

这是同 JVM 内的扩展机制，不是安全沙箱。仅安装可信插件：插件拥有进程权限，可以访问宿主数据，恶意代码、`System.exit`、内存耗尽、死锁等无法由加载器隔离。API 版本 1 仅指插件生命周期协议，不代表宿主全部业务 API 永久保持二进制兼容；建议针对部署的宿主版本编译。
