package top.yzljc.qqbot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // method = 0 -> Napcat缇よ亰 method = 1 -> 瀹樻柟鏈哄櫒浜虹鑱?method = 2 -> 瀹樻柟鏈哄櫒浜虹兢鑱?
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

        try {
            target.execute(sender, label, args);
            return true;
        } catch (Exception e) {
            log.error("执行命令 {} 时发生异常，方法{}", commandLabel, label, e);
            if (label.equalsIgnoreCase("0")) {
                sender.reply("执行命令时发生内部错误", false);
            } else {
                sender.replyMarkdown(label, "> 执行命令时发生内部错误，请联系开发者处理！");
            }
            return false;
        }
    }
}