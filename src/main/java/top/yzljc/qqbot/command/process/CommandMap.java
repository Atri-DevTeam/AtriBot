package top.yzljc.qqbot.command.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CommandMap {
    private static final Logger log = LoggerFactory.getLogger(CommandMap.class);
    private final Map<String, Command> knownCommands = new HashMap<>();

    public void register(String fallbackPrefix, Command command) {
        knownCommands.put(command.getUid().toLowerCase(), command);
        if (command.getAliases() != null) {
            for (String alias : command.getAliases()) {
                knownCommands.put(alias.toLowerCase(), command);
            }
        }
    }

    public boolean dispatch(CommandSender sender, String cmdLine) {
        String[] parts = cmdLine.split("\\s+", 2); // 分割命令和参数
        String commandLabel = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];

        Command target = knownCommands.get(commandLabel);
        if (target == null) {
            return false;
        }

        try {
            target.execute(sender, commandLabel, args);
            return true;
        } catch (Exception e) {
            log.error("执行命令 {} 时发生异常", commandLabel, e);
            sender.reply("执行命令时发生内部错误",false);
            return false;
        }
    }
}