package top.yzljc.atricore.command;

import top.yzljc.atricore.AtriProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atricore.event.EventManager;
import top.yzljc.atricore.event.events.UserRunCommandEvent;
import top.yzljc.atricore.platform.Platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandMap
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
public class CommandMap {
    private static final Logger log = LoggerFactory.getLogger(CommandMap.class);
    private volatile Registry registry = new Registry(Map.of(), Map.of(), Map.of(), Map.of());
    private final Map<String, Command> coreCommands = new LinkedHashMap<>();
    private final Map<String, List<ScopedCommand>> pluginCommands = new LinkedHashMap<>();

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

    public Command getCommand(String name, Platform platform) {
        if (name == null || name.isBlank()) return null;
        Registry current = registry;
        String label = name.toLowerCase(Locale.ROOT);
        Command command = platform == null ? null : current.scoped().getOrDefault(platform, Map.of()).get(label);
        if (command == null) command = current.commands().get(label);
        return command instanceof ScopedCommand plugin && !plugin.supports(platform) ? null : command;
    }

    public List<CommandFeature> getCommands(Platform platform) {
        Registry current = registry;
        Map<String, Command> commands = new LinkedHashMap<>(current.commands());
        if (platform != null) commands.putAll(current.scoped().getOrDefault(platform, Map.of()));
        return commands.values().stream().distinct().filter(CommandFeature.class::isInstance)
                .map(CommandFeature.class::cast)
                .filter(command -> getCommand(command.getName(), platform) == command)
                .filter(command -> !(command instanceof ScopedCommand plugin) || plugin.supports(platform)).toList();
    }

    public synchronized void clear() {
        rebuild(Map.of(), pluginCommands);
    }

    public synchronized void replaceCoreCommands(List<? extends Command> commands) {
        Map<String, Command> next = new LinkedHashMap<>();
        commands.forEach(command -> registerCore(next, command));
        rebuild(next, pluginCommands);
    }

    public synchronized void registerPluginCommands(String owner, List<? extends ScopedCommand> commands) {
        if (pluginCommands.containsKey(owner)) throw new IllegalStateException("插件指令已注册: " + owner);
        Map<String, List<ScopedCommand>> next = new LinkedHashMap<>(pluginCommands);
        next.put(owner, List.copyOf(commands));
        rebuild(coreCommands, next);
    }

    public synchronized void unregisterPluginCommands(String owner, List<? extends ScopedCommand> commands) {
        if (commands.equals(pluginCommands.get(owner))) {
            Map<String, List<ScopedCommand>> next = new LinkedHashMap<>(pluginCommands);
            next.remove(owner);
            rebuild(coreCommands, next);
        }
    }

    private void rebuild(Map<String, Command> core, Map<String, List<ScopedCommand>> plugins) {
        Map<String, Command> updated = new LinkedHashMap<>(core);
        Map<Platform, Map<String, Command>> scoped = new HashMap<>();
        plugins.forEach((owner, commands) -> {
            for (ScopedCommand command : commands) {
                updated.putIfAbsent(owner + ":" + command.getName(), command);
                command.getAliases().forEach(alias -> updated.putIfAbsent(owner + ":" + alias, command));
                if (command.getPlatforms().isEmpty()) {
                    updated.putIfAbsent(command.getName(), command);
                    command.getAliases().forEach(alias -> updated.putIfAbsent(alias, command));
                } else {
                    for (Platform platform : command.getPlatforms()) {
                        Map<String, Command> labels = scoped.computeIfAbsent(platform, _ -> new LinkedHashMap<>());
                        registerScoped(labels, command.getName(), command);
                        command.getAliases().forEach(alias -> registerScoped(labels, alias, command));
                    }
                }
            }
        });
        Map<String, String> prefixless = new HashMap<>();
        Map<Platform, Map<String, String>> scopedPrefixless = new HashMap<>();
        for (Command command : new HashSet<>(updated.values())) {
            if (!(command instanceof CommandFeature feature)) continue;
            String label = command instanceof ScopedCommand plugin ? plugin.getQualifiedName()
                    : command.getName().toLowerCase(Locale.ROOT);
            for (String alias : feature.getPrefixlessAliases()) {
                if (command instanceof ScopedCommand plugin && !plugin.getPlatforms().isEmpty()) {
                    for (Platform platform : plugin.getPlatforms()) {
                        String previous = scopedPrefixless.computeIfAbsent(platform, _ -> new HashMap<>()).putIfAbsent(alias, label);
                        if (previous != null && !previous.equals(label))
                            throw new IllegalArgumentException("无前缀触发词冲突: " + alias);
                    }
                    continue;
                }
                String dispatchLabel = updated.get(alias) == command ? alias : label;
                String previous = prefixless.putIfAbsent(alias, dispatchLabel);
                if (previous != null && !previous.equals(dispatchLabel)) {
                    throw new IllegalArgumentException("无前缀触发词冲突: " + alias + " (" + previous + ", " + dispatchLabel + ")");
                }
            }
        }
        scoped.replaceAll((_, labels) -> Map.copyOf(labels));
        scopedPrefixless.replaceAll((_, labels) -> Map.copyOf(labels));
        Registry next = new Registry(Map.copyOf(updated), Map.copyOf(prefixless), Map.copyOf(scoped), Map.copyOf(scopedPrefixless));
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

    private static void registerScoped(Map<String, Command> labels, String label, Command command) {
        Command previous = labels.putIfAbsent(label.toLowerCase(Locale.ROOT), command);
        if (previous != null && previous != command) throw new IllegalArgumentException("平台指令冲突: " + label);
    }

    /**
     * 将平台文本转换为现有分发器使用的指令行，显式前缀优先于无前缀触发词
     *
     * @param input  已去除平台提及标记的消息文本
     * @param prefix 当前配置的指令前缀
     * @return 去除前缀或替换触发词后的指令行，普通消息返回 null
     */
    public String resolveCommandLine(String input, String prefix) {
        return resolveCommandLine(input, prefix, null);
    }

    public String resolveCommandLine(String input, String prefix, Platform platform) {
        if (input == null) return null;
        String text = input.trim();
        if (text.startsWith(prefix)) return text.substring(prefix.length());
        if (text.isEmpty()) return null;
        String[] parts = text.split("\\s+", 2);
        Registry current = registry;
        String alias = parts[0].toLowerCase(Locale.ROOT);
        String label = platform == null ? null : current.scopedPrefixless().getOrDefault(platform, Map.of()).get(alias);
        if (label == null) label = current.prefixlessAliases().get(alias);
        return label == null ? null : label + (parts.length > 1 ? " " + parts[1] : "");
    }

    /**
     * 按文本分发规则判断指令输入，不执行指令或触发事件
     *
     * @param input  已去除平台提及标记的消息文本，允许为 null
     * @param prefix 当前配置的指令前缀
     * @return 包含显式前缀或命中无前缀触发词时返回 true；显式前缀后的指令可以未注册
     */
    public boolean isCommand(String input, String prefix) {
        return resolveCommandLine(input, prefix) != null;
    }

    private record Registry(Map<String, Command> commands, Map<String, String> prefixlessAliases,
                            Map<Platform, Map<String, Command>> scoped,
                            Map<Platform, Map<String, String>> scopedPrefixless) {
    }

    public synchronized Map<String, CommandExecutor> snapshotExecutors() {
        Map<String, CommandExecutor> executors = new LinkedHashMap<>();
        for (Command command : coreCommands.values()) {
            if (command instanceof CommandFeature feature && feature.getExecutor() != null) {
                executors.putIfAbsent(command.getName().toLowerCase(Locale.ROOT), feature.getExecutor());
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

        Command target = getCommand(commandLabel, sender.getPlatform());
        if (target == null) {
            return false;
        }

        String groupId = null;
        if (sender instanceof QQCommandSender qqSender && qqSender.getGroupId() != null) {
            groupId = qqSender.getGroupId();
        }
        if (groupId != null) {
            String settingsKey = target instanceof ScopedCommand plugin ? plugin.getQualifiedName() : target.getName();
            var disabled = AtriProvider.getInstance().getBotCommand().disabledReason(settingsKey, groupId);
            if (disabled.isPresent()) {
                sender.sendMessage(disabled.get());
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
