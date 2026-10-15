# 指令

[文档目录](../README.md)

## 声明资源

在 `src/main/resources/atribot.yml` 声明指令，其他资源路径可通过 `BotCommand.commandResource()` 指定

```yaml
commands:
  ping:
    description: "检查机器人响应"
    usage: "/ping"
    aliases: ["p"]
    prefixless-aliases: ["测试响应"]
```

| 字段 | 含义 |
| --- | --- |
| 主键 `ping` | 主指令名 |
| `description` | 指令说明 |
| `usage` | 执行器返回 false 时使用的用法 |
| `aliases` | 带指令前缀的别名 |
| `prefixless-aliases` | 无需前缀的完整触发词，不允许包含空白字符 |

无前缀触发词按消息的第一个空白分隔词查找，不执行全量正则匹配，重复触发词冲突会使注册失败

声明资源必须对 `CommandManager` 的类加载器可见，外部文件不会自动按路径读取

## 注册执行器

调用 `provider.initialize()` 后绑定执行器，指令监听器已由 core 自动注册：

```java
import top.yzljc.atricore.command.CommandManager;

CommandManager.getCommand("ping").setExecutor((sender, command, label, args) -> {
    if (args.length != 0) return false;
    sender.sendMessage("pong");
    return true;
});
```

在初始化与启动之间执行上述注册代码，指令监听器由框架统一注册

指令不存在时 `getCommand()` 返回 null，应先保证声明资源已加载，`CommandManager.reload()` 会重新读取声明并按主指令名保留已绑定的执行器

## 执行参数

```java
boolean onCommand(CommandSender sender, Command command, String label, String[] args)
```

| 参数 | 含义 |
| --- | --- |
| `sender` | 指令来源及回复能力 |
| `command` | 匹配到的主指令 |
| `label` | 本次分发标签，可能是别名或无前缀映射后的名称 |
| `args` | 标签之后按空白拆分的参数，不含指令本身 |

`/p one two` 对应主指令 `ping`、标签 `p` 和参数 `[one, two]`

参数按空白字符拆分，引号不会将多个词合并为一个参数

执行器返回 true 表示已经处理，返回 false 时 `CommandFeature` 发送非空的 `usage` 提示

`CommandManager.dispatch()` 的返回值含义不同：true 表示已找到并处理分发入口，也包括业务禁用、事件取消或执行异常，不代表业务成功

## 判断与主动分发

```java
boolean commandInput = CommandManager.isCommand(text);
String commandLine = CommandManager.resolveCommandLine(sender, text);
if (commandLine != null) {
    CommandManager.dispatch(sender, commandLine);
}
```

`isCommand()` 只判断显式前缀或无前缀触发词，不验证指令是否存在，也不验证执行权限

`dispatch()` 接收已经去除显式前缀或完成触发词映射的指令行，直接调用时不会重复处理 `/`

平台事件入口会处理各自的消息文本，直接调用上述方法时应先去除平台提及标记

## 平台发送者

| 类型 | 场景 |
| --- | --- |
| `QQCommandSender` | QQ 群聊和 C2C |
| `QQGuildCommandSender` | QQ 文字子频道和频道私信 |
| `NapcatCommandSender` | Napcat 群聊和私聊 |
| `ConsoleCommandSender` | 控制台 |

公共能力包含用户标识、名称、权限判断和文本回复，Markdown、图片、会话标识等能力通过对应接口使用

`getPlatform()` 在控制台等无平台来源下可能返回 null，业务指令应检查平台类型后再使用特定能力

## 权限与事件

业务权限在执行器中通过 `sender.hasPermission(permission)` 检查，也可在 `UserRunCommandEvent` 中统一检查

匹配指令后同步触发 `UserRunCommandEvent`，取消该事件会阻止执行器调用，监听规则见 [事件](events.md)

## 按平台注册扩展指令

`ScopedCommand` 声明限定名称和支持平台，`CommandManager.registerPluginCommands(owner, commands)` 按所有者注册，卸载时使用对应的 `unregisterPluginCommands`

指定平台的指令在该平台查找时优先，空平台集合表示通用指令，限定名称使用 `owner:name`

core 提供注册机制，不包含插件目录扫描、类加载或插件生命周期管理
