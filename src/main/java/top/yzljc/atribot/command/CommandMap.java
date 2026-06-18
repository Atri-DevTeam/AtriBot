package top.yzljc.atribot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.UserRunCommandEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class CommandMap {
    private static final Logger log = LoggerFactory.getLogger(CommandMap.class);
    private final Map<String, Command> knownCommands = new LinkedHashMap<>();

    public void register(String fallbackPrefix, Command command) {
        if (command == null || command.getName() == null || command.getName().isBlank()) {
            return;
        }

        knownCommands.put(command.getName().toLowerCase(), command);
        if (command.getAliases() != null) {
            for (String alias : command.getAliases()) {
                if (alias != null && !alias.isBlank()) {
                    knownCommands.put(alias.toLowerCase(), command);
                }
            }
        }
    }

    public Command getCommand(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return knownCommands.get(name.toLowerCase());
    }

    public void clear() {
        knownCommands.clear();
    }

    public Map<String, CommandExecutor> snapshotExecutors() {
        Map<String, CommandExecutor> executors = new LinkedHashMap<>();
        for (Command command : knownCommands.values()) {
            if (command instanceof CommandFeature feature && feature.getExecutor() != null) {
                executors.putIfAbsent(command.getName().toLowerCase(), feature.getExecutor());
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
        String commandLabel = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];

        Command target = knownCommands.get(commandLabel);
        if (target == null) {
            return false;
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
