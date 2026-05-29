package top.yzljc.atribot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.impl.UserRunCommandEvent;

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

    // [0] Napcat [1] 官机私聊 [2] 官机群聊
    public boolean dispatch(CommandSender sender, String cmdLine, String label) {
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

        // onebot-only：仅 label "0" 响应，官机当作不存在
        if (!"0".equals(label) && target.isOnebotOnly()) {
            return false;
        }
        // official-only：仅官机响应，OneBot 当作不存在
        if ("0".equals(label) && target.isOfficialOnly()) {
            return true;
        }

        try {
            UserRunCommandEvent event = new UserRunCommandEvent(sender, target, label, commandLabel, args, false);
            EventManager.getInstance().callEvent(event);
            if (event.isCancelled()) {
                return true;
            }
            target.execute(sender, label, args);
            return true;
        } catch (Exception e) {
            log.error("执行命令 {} 时发生异常，方法{}", commandLabel, label, e);
            if (label.equalsIgnoreCase("0")) {
                sender.reply("执行命令时发生内部错误", false);
            } else {
                sender.replyMarkdown(label, "> 执行命令时发生内部错误，请联系开发者处理！");
            }
            return true;
        }
    }
}