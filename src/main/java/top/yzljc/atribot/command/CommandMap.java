package top.yzljc.atribot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.UserRunCommandEvent;
import top.yzljc.atribot.plugin.PluginCommand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandMap
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public class CommandMap {
    private static final Logger log = LoggerFactory.getLogger(CommandMap.class);
    private volatile Registry registry = new Registry(Map.of(), Map.of());
    private final Map<String, Command> coreCommands = new LinkedHashMap<>();
    private final Map<String, List<PluginCommand>> pluginCommands = new LinkedHashMap<>();

    public synchronized void register(String fallbackPrefix, Command command) {
        Map<String, Command> next = new LinkedHashMap<>(coreCommands);
        registerCore(next, command);
        rebuild(next, pluginCommands);
    }

    private void registerCore(Map<String, Command> commands, Command command) {
        if (command == null || command.getName() == null || command.getName().isBlank()) {
            return;
        }

        commands.put(command.getName().toLowerCase(Locale.ROOT), command);
        if (command.getAliases() != null) {
            for (String alias : command.getAliases()) {
                if (alias != null && !alias.isBlank()) {
                    commands.put(alias.toLowerCase(Locale.ROOT), command);
                }
            }
        }
    }

    public Command getCommand(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return registry.commands().get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized void clear() {
        rebuild(Map.of(), pluginCommands);
    }

    public synchronized void replaceCoreCommands(List<? extends Command> commands) {
        Map<String, Command> next = new LinkedHashMap<>();
        commands.forEach(command -> registerCore(next, command));
        rebuild(next, pluginCommands);
    }

    public synchronized void registerPluginCommands(String owner, List<PluginCommand> commands) {
        if (pluginCommands.containsKey(owner)) throw new IllegalStateException("插件指令已注册: " + owner);
        Map<String, List<PluginCommand>> next = new LinkedHashMap<>(pluginCommands);
        next.put(owner, List.copyOf(commands));
        rebuild(coreCommands, next);
    }

    public synchronized void unregisterPluginCommands(String owner, List<PluginCommand> commands) {
        if (commands.equals(pluginCommands.get(owner))) {
            Map<String, List<PluginCommand>> next = new LinkedHashMap<>(pluginCommands);
            next.remove(owner);
            rebuild(coreCommands, next);
        }
    }

    private void rebuild(Map<String, Command> core, Map<String, List<PluginCommand>> plugins) {
        Map<String, Command> updated = new LinkedHashMap<>(core);
        plugins.forEach((owner, commands) -> {
            for (PluginCommand command : commands) {
                updated.putIfAbsent(owner + ":" + command.getName(), command);
                command.getAliases().forEach(alias -> updated.putIfAbsent(owner + ":" + alias, command));
                updated.putIfAbsent(command.getName(), command);
                command.getAliases().forEach(alias -> updated.putIfAbsent(alias, command));
            }
        });
        Map<String, String> prefixless = new HashMap<>();
        for (Command command : new HashSet<>(updated.values())) {
            if (!(command instanceof CommandFeature feature)) continue;
            String label = command instanceof PluginCommand plugin ? plugin.getQualifiedName()
                    : command.getName().toLowerCase(Locale.ROOT);
            for (String alias : feature.getPrefixlessAliases()) {
                String dispatchLabel = updated.get(alias) == command ? alias : label;
                String previous = prefixless.putIfAbsent(alias, dispatchLabel);
                if (previous != null && !previous.equals(dispatchLabel)) {
                    throw new IllegalArgumentException("无前缀触发词冲突: " + alias + " (" + previous + ", " + dispatchLabel + ")");
                }
            }
        }
        Registry next = new Registry(Map.copyOf(updated), Collections.unmodifiableMap(prefixless));
        if (core != coreCommands) {
            coreCommands.clear();
            coreCommands.putAll(core);
        }
        if (plugins != pluginCommands) {
            pluginCommands.clear();
            pluginCommands.putAll(plugins);
        }
        registry = next;
    }

    /**
     * 将平台文本转换为现有分发器使用的指令行，显式前缀优先于无前缀触发词
     *
     * @param input 已去除平台提及标记的消息文本
     * @param prefix 当前配置的指令前缀
     * @return 去除前缀或替换触发词后的指令行，普通消息返回 null
     */
    public String resolveCommandLine(String input, String prefix) {
        if (input == null) return null;
        String text = input.trim();
        if (text.startsWith(prefix)) return text.substring(prefix.length());
        if (text.isEmpty()) return null;
        String[] parts = text.split("\\s+", 2);
        String label = registry.prefixlessAliases().get(parts[0].toLowerCase(Locale.ROOT));
        return label == null ? null : label + (parts.length > 1 ? " " + parts[1] : "");
    }

    /**
     * 按文本分发规则判断指令输入，不执行指令或触发事件
     *
     * @param input 已去除平台提及标记的消息文本，允许为 null
     * @param prefix 当前配置的指令前缀
     * @return 包含显式前缀或命中无前缀触发词时返回 true；显式前缀后的指令可以未注册
     */
    public boolean isCommand(String input, String prefix) {
        return resolveCommandLine(input, prefix) != null;
    }

    private record Registry(Map<String, Command> commands, Map<String, String> prefixlessAliases) {}

    public synchronized Map<String, CommandExecutor> snapshotExecutors() {
        Map<String, CommandExecutor> executors = new LinkedHashMap<>();
        for (Command command : coreCommands.values()) {
            if (command instanceof CommandFeature feature && feature.getExecutor() != null) {
                executors.putIfAbsent(command.getName().toLowerCase(Locale.ROOT), feature.getExecutor());
            }
        }
        return executors;
    }

    public synchronized Map<String, SlashCommandExecutor> snapshotSlashExecutors() {
        Map<String, SlashCommandExecutor> executors = new LinkedHashMap<>();
        for (Command command : coreCommands.values()) {
            if (command instanceof CommandFeature feature && feature.getExplicitSlashExecutor() != null) {
                executors.putIfAbsent(command.getName().toLowerCase(Locale.ROOT), feature.getExplicitSlashExecutor());
            }
        }
        return executors;
    }

    public boolean dispatch(CommandSender sender, String cmdLine) {
        if (cmdLine == null) {
            return false;
        }

        String trimmed = cmdLine.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        String[] parts = trimmed.split("\\s+", 2);
        String commandLabel = parts[0].toLowerCase(Locale.ROOT);
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];

        Command target = registry.commands().get(commandLabel);
        if (target == null) {
            return false;
        }

        String groupId = null;
        if (sender instanceof QQCommandSender qqSender && qqSender.getGroupId() != null) {
            groupId = qqSender.getGroupId();
        }
        if (groupId != null) {
            String settingsKey = target instanceof PluginCommand plugin ? plugin.getQualifiedName() : target.getName();
            var disabled = CommandDisableService.resolve(settingsKey, groupId);
            if (disabled.isPresent()) {
                sender.sendMessage(disabled.get().reason());
                return true;
            }
        }

        try {
            UserRunCommandEvent event = new UserRunCommandEvent(sender, target, commandLabel, commandLabel, args, false);
            EventManager.getInstance().callEvent(event);
            if (event.isCancelled()) {
                return true;
            }
            target.execute(sender, commandLabel, args);
            return true;
        } catch (Exception e) {
            log.error("执行命令 {} 时发生异常，问题: ", commandLabel, e);
            sender.sendMessage("执行命令时发生内部错误，请联系开发者处理！");
            return true;
        }
    }
}
