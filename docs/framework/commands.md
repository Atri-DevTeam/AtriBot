# 指令

[文档目录](../README.md) · [事件](events.md) · [插件开发](plugins.md)

## 组成

| 类型 | 职责 |
| --- | --- |
| `Command` | 名称、别名、描述、用法和执行入口 |
| `CommandFeature` | 核心命令，关联文本和 Slash 执行器 |
| `CommandSender` | 用户身份、权限检查、文本回复 |
| `CommandExecutor` | 文本位置参数执行器 |
| `SlashCommandExecutor` | 结构化 Slash 参数执行器 |
| `CommandManager` | 加载声明、平台事件到命令的转换 |
| `CommandMap` | 名称和别名映射、文本分发 |

源码位于 [command](../../src/main/java/top/yzljc/atribot/command)

## 声明与注册

核心命令在 [atribot.yml](../../src/main/resources/atribot.yml) 声明：

```yaml
commands:
  example:
    description: 示例命令
    usage: /example <内容>
    aliases:
      - ex
    options:
      - name: content
        type: 3
        description: 内容
        required: true
```

该文件从 JAR 资源读取，修改后需要重新构建。运行目录放置同名文件不会替换核心声明

在 `Atri.onEnable()` 绑定执行器：

```java
CommandManager.getCommand("example").setExecutor(new ExampleCommand());
```

声明与执行器缺一不可。`CommandManager.reload()` 重建核心命令定义，保留已绑定执行器与插件命令，不负责重载插件 JAR

## 文本指令

```java
package example;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;

public final class ExampleCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!sender.hasPermission("example.use")) {
            sender.sendMessage("权限不足");
            return true;
        }
        if (args.length == 0) return false;
        sender.sendMessage(String.join(" ", args));
        return true;
    }
}
```

`CommandMap` 按空白拆分参数，没有引号、转义或命名参数解析。QQ 等文本平台不会提供参数的名称和类型，参数含义由执行器解释

核心文本执行器返回 `false` 时，`CommandFeature` 在配置了用法的情况下发送用法提示；返回 `true` 表示该次调用已处理

分发前触发 `UserRunCommandEvent`，取消后不再执行。QQ 群文本指令还会检查 `CommandDisableService` 的禁用规则

### 无前缀触发词

在指令声明中添加 `prefixless-aliases`，即可使用不带 `/` 的入口。核心 `atribot.yml` 和插件 `plugin.yml` 均支持：

```yaml
commands:
  hyp:
    description: Hypixel 查询
    prefixless-aliases: [hyp, 海皮查询]
```

`海皮查询 hotm Steve` 与 `/hyp hotm Steve` 使用相同执行器和参数。触发词同时是该指令当前注册的名称或别名时，`label` 保留触发词，执行器可据此区分入口；其余触发词使用核心指令名，插件使用 `插件名:指令名`，避免短名称冲突

- 触发词必须是非空文本，不能包含空白字符；按消息第一个词匹配，英文字母不区分大小写
- 触发词后只能是消息结尾或空白，`海皮查询一下` 不匹配 `海皮查询`
- 显式指令前缀优先，普通 `aliases` 不自动开放无前缀入口
- 未命中时作为普通消息跳过，不发送未知指令提示，也不计入指令次数
- 匹配后沿用 `CommandMap.dispatch()`，保留指令事件、权限检查、群禁用规则和用法提示

触发词在注册、重载和插件卸载时建立或更新 HashMap 索引，每条消息按首词查表，不遍历所有指令。不同指令重复声明同一触发词会使本次注册或重载失败，原有映射保留

适用于 QQ 官方群聊、C2C、频道 @ 消息、频道私信，Napcat 群聊与私聊，以及 KOOK 频道与私信。QQ 群不带 @ 的触发依赖平台投递全量群消息。Discord 仍使用 Slash Command，业务执行器自身的平台限制仍然有效

消息原文保持不变，普通消息监听器仍可收到该事件。内置的「指令帮助」和「反馈与建议」已迁入此配置，分别指向 `help` 和 `feedback`

### 判断指令输入

```java
boolean commandInput = CommandManager.isCommand(text);
```

使用与文本分发相同的前缀和触发词索引，不执行指令、不触发事件、不增加调用次数，也不检查权限或平台是否支持执行器。传入文本应已去除平台的前置提及标记

| 输入 | 结果 |
| --- | --- |
| 配置前缀为 `/` 时，输入 `/help`、`/unknown` 或 `/` | `true`，均属于显式指令输入 |
| 命中已注册的无前缀触发词，可附带空白分隔的参数 | `true` |
| 未命中的普通文本、空白、`null` | `false` |

`QQMessage.isCommand()` 同样调用此入口，并保留原有签到词兼容。需要严格按框架注册规则判断时，使用 `CommandManager.isCommand(text)`。插件注册、卸载或指令重载后，判断使用更新后的索引

## Slash Command

结构化入口保留独立方法：

```java
boolean onCommand(SlashCommandSender sender, Command command,
                  String label, SlashCommandArguments args);
```

通过 `args.getString("content")`、`getInteger`、`getBoolean` 等方法按名称取值。`getCommandPath()` 保存子命令路径，`getOptionList()` 保存选项，缺少值时返回空值或指定默认值

```java
CommandManager.getCommand("example").setSlashExecutor((sender, command, label, args) -> {
    if (!sender.hasPermission("example.use")) {
        sender.sendMessage("权限不足");
        return true;
    }
    sender.sendMessage(args.getString("content", ""));
    return true;
});
```

同一个类也可同时实现两种执行器。未显式绑定 Slash 执行器时，`CommandFeature` 会检查文本执行器是否实现了 `SlashCommandExecutor`

`options` 当前使用 Discord 参数类型编号，`3` 为字符串。完整字段见 [CommandOptionDefinition](../../src/main/java/top/yzljc/atribot/command/CommandOptionDefinition.java)，包括子命令、候选值、数值范围和频道类型

Discord 分发直接调用 Slash 执行器，不经过 `CommandMap.dispatch()`。因此文本入口的参数拆分、`UserRunCommandEvent`、群禁用检查和自动用法提示不能推定为 Slash 入口也具备；权限和参数错误由执行器处理

`SlashCommandArguments.toArray()` 只提供兼容性转换，会丢失名称和类型。平台专有交互数据由对应平台的参数和发送者类型提供

## 平台能力

`CommandSender` 提供 `getUserId()`、`getUsername()`、权限判断和文本发送。`getPlatform()` 对控制台等无平台来源返回 `null`

富文本或专有操作通过类型判断调用：

```java
if (sender instanceof KookCommandSender kook) {
    kook.sendKMarkdown("**完成**");
}
```

常用接口包括 `QQCommandSender`、`QQGuildCommandSender`、`NapcatCommandSender`、`DiscordCommandSender`、`KookCommandSender`。共享业务逻辑可复用，平台输出保留各自实现

## 权限

核心声明中的名称、描述和 Slash 选项不自动构成业务授权。执行器应在操作前调用 `sender.hasPermission(...)`

插件命令可在 `plugin.yml` 声明 `permission`，由 `PluginCommand` 在执行前检查。具体语义见 [账号与权限](accounts.md)
