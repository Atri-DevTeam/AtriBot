package top.yzljc.atribot.service.runtime;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.command.CommandDefinition;
import top.yzljc.atribot.command.CommandFeature;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.impl.ConsoleSenderImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName ConsoleManager
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.runtime
 */
public class ConsoleManager {
    private static final Logger log = LoggerFactory.getLogger(ConsoleManager.class);
    private static final Path HISTORY_FILE = Path.of("data", "atri_history");
    private static volatile LineReader reader;

    public static void printConsole(String message) {
        LineReader current = reader;
        if (current != null) {
            current.printAbove(message);
        } else {
            System.err.print(message);
        }
    }

    public static void start() {
        Thread thread = new Thread(ConsoleManager::run, "Console-Listener");
        thread.start();
    }

    private static void run() {
        ConsoleSenderImpl sender = new ConsoleSenderImpl();
        try {
            runJLine(sender);
        } catch (Exception e) {
            log.warn("[!] 自定义终端初始化失败，已切换到基本输出调用: {}", e.toString());
            runFallback(sender);
        }
    }

    private static void runJLine(ConsoleSenderImpl sender) throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        DefaultHistory history = new DefaultHistory();

        Map<String, Object> variables = new HashMap<>();
        variables.put(LineReader.HISTORY_FILE, HISTORY_FILE);
        // 候选数量过多时直接列出，不再询问 "do you wish to see all N possibilities?"
        variables.put(LineReader.LIST_MAX, -1);

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variables(variables)
                .completer(new CommandNameCompleter())
                .history(history)
                .build();
        ConsoleManager.reader = reader;

        loadHistory(history);

        String prompt = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("atri> ")
                .toAnsi();

        try {
            while (true) {
                String line;
                try {
                    line = reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    continue; // Ctrl+C：清空当前行继续输入
                } catch (EndOfFileException e) {
                    break; // Ctrl+D：退出控制台
                }
                if (line == null) {
                    break;
                }
                dispatch(sender, line);
            }
        } finally {
            ConsoleManager.reader = null;
            saveHistory(history);
            terminal.close();
        }
    }

    private static void runFallback(ConsoleSenderImpl sender) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                dispatch(sender, line);
            }
        } catch (IOException e) {
            log.warn("[!] 控制台输入读取失败: {}", e.toString());
        }
    }

    private static void dispatch(CommandSender sender, String line) {
        String content = line.trim();
        if (content.isEmpty()) {
            return;
        }
        if (content.startsWith("/")) {
            content = content.substring(1);
        }
        String[] parts = content.split("\\s+", 2);
        String label = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];

        CommandFeature command = CommandManager.getCommand(label);
        if (command == null) {
            sender.sendMessage("未知命令: " + label + "，使用 help 查看可用指令");
            return;
        }
        command.execute(sender, label, args);
    }

    private static void loadHistory(DefaultHistory history) {
        try {
            if (Files.exists(HISTORY_FILE)) {
                history.load();
            }
        } catch (Exception e) {
            log.warn("[!] 加载控制台历史记录失败: {}", e.toString());
        }
    }

    private static void saveHistory(DefaultHistory history) {
        try {
            Files.createDirectories(HISTORY_FILE.getParent());
            history.save();
        } catch (Exception e) {
            log.warn("保存控制台历史记录失败: {}", e.toString());
        }
    }

    /** Tab 补全：命令名 + 别名 */
    private static class CommandNameCompleter implements Completer {
        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word();
            for (CommandDefinition definition : CommandManager.getDefinitions()) {
                if (definition.name().startsWith(word)) {
                    candidates.add(new Candidate(definition.name()));
                }
                for (String alias : definition.aliases()) {
                    if (alias.startsWith(word)) {
                        candidates.add(new Candidate(alias));
                    }
                }
            }
        }
    }
}
