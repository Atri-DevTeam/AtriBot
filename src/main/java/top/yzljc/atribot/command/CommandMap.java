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

/**
 * @Author YZ_Ljc_
 * @ClassName CommandMap
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public class CommandMap {
    private static final Logger log = LoggerFactory.getLogger(CommandMap.class);
    private volatile Map<String, Command> knownCommands = Map.of();
    private final Map<String, Command> coreCommands = new LinkedHashMap<>();
    private final Map<String, List<PluginCommand>> pluginCommands = new LinkedHashMap<>();

    public synchronized void register(String fallbackPrefix, Command command) {
        registerCore(command);
        rebuild();
    }

    private void registerCore(Command command) {
        if (command == null || command.getName() == null || command.getName().isBlank()) {
            return;
        }

        coreCommands.put(command.getName().toLowerCase(Locale.ROOT), command);
        if (command.getAliases() != null) {
            for (String alias : command.getAliases()) {
                if (alias != null && !alias.isBlank()) {
                    coreCommands.put(alias.toLowerCase(Locale.ROOT), command);
                }
            }
        }
    }

    public Command getCommand(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return knownCommands.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized void clear() {
        coreCommands.clear();
        rebuild();
    }

    public synchronized void replaceCoreCommands(List<? extends Command> commands) {
        coreCommands.clear();
        commands.forEach(this::registerCore);
        rebuild();
    }

    public synchronized void registerPluginCommands(String owner, List<PluginCommand> commands) {
        if (pluginCommands.containsKey(owner)) throw new IllegalStateException("插件指令已注册: " + owner);
        pluginCommands.put(owner, List.copyOf(commands));
        rebuild();
    }

    public synchronized void unregisterPluginCommands(String owner, List<PluginCommand> commands) {
        if (commands.equals(pluginCommands.get(owner))) {
            pluginCommands.remove(owner);
            rebuild();
        }
    }

    private void rebuild() {
        Map<String, Command> updated = new LinkedHashMap<>(coreCommands);
        pluginCommands.forEach((owner, commands) -> {
            for (PluginCommand command : commands) {
                updated.putIfAbsent(owner + ":" + command.getName(), command);
                command.getAliases().forEach(alias -> updated.putIfAbsent(owner + ":" + alias, command));
                updated.putIfAbsent(command.getName(), command);
                command.getAliases().forEach(alias -> updated.putIfAbsent(alias, command));
            }
        });
        knownCommands = Map.copyOf(updated);
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

        Command target = knownCommands.get(commandLabel);
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
