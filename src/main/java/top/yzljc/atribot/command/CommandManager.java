package top.yzljc.atribot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;

import java.io.InputStream;
import java.util.*;

public class CommandManager implements Listener {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);
    private static final String COMMAND_FILE = Properties.ATRIBOT;
    private static final String COMMAND_PREFIX = Config.getInstance().getCommandPrefix();
    private static final String FALLBACK_PREFIX = "atri-core";

    private static final CommandMap commandMap = new CommandMap();
    private static volatile List<CommandDefinition> registeredDefinitions = List.of();

    static {
        reload();
    }

    public static CommandFeature getCommand(String name) {
        return (CommandFeature) commandMap.getCommand(name);
    }

    public static synchronized void reload() {
        Map<String, CommandExecutor> executors = commandMap.snapshotExecutors();
        List<CommandDefinition> definitions = loadDefinitions();
        registeredDefinitions = List.copyOf(definitions);

        commandMap.clear();
        for (CommandDefinition definition : definitions) {
            CommandFeature command = new CommandFeature(definition);
            CommandExecutor executor = executors.get(definition.name().toLowerCase());
            if (executor != null) {
                command.setExecutor(executor);
            }
            commandMap.register(FALLBACK_PREFIX, command);
        }

        log.info("命令配置已加载，共 {} 个命令", definitions.size());
    }

    public static List<CommandDefinition> getDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    private static List<CommandDefinition> loadDefinitions() {
        try (InputStream in = CommandManager.class.getClassLoader().getResourceAsStream(COMMAND_FILE)) {
            if (in == null) {
                log.warn("未找到命令配置资源 {}", COMMAND_FILE);
                return List.of();
            }
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            if (data == null) {
                return List.of();
            }

            Object commandsObj = data.get("commands");
            if (!(commandsObj instanceof Map<?, ?> rawCommands)) {
                log.warn("命令配置 {} 中缺少 commands 节点", COMMAND_FILE);
                return List.of();
            }

            List<CommandDefinition> definitions = new ArrayList<>();
            for (Map.Entry<?, ?> entry : rawCommands.entrySet()) {
                if (entry.getKey() instanceof String name && entry.getValue() instanceof Map<?, ?> rawDefinition) {
                    Map<String, Object> definition = new LinkedHashMap<>();
                    rawDefinition.forEach((key, value) -> {
                        if (key instanceof String actualKey) {
                            definition.put(actualKey, value);
                        }
                    });
                    definitions.add(CommandDefinition.from(name, definition));
                }
            }
            return definitions;
        } catch (Exception e) {
            log.error("加载命令配置 {} 失败", COMMAND_FILE, e);
            return List.of();
        }
    }

    @EventHandler
    public void processCommand(NapcatGroupMessageEvent event) {
        String userInput = event.getMessage().getContent().trim();

        if (!userInput.startsWith(COMMAND_PREFIX)) {
            return;
        }

        User eventUser = event.getUser();

        if (Objects.equals(eventUser.getUserId(), Config.getInstance().getNapcatBotUin())) {
            return;
        }

        String msgId = event.getMessage().getMessageId();
        String commandContent = userInput.substring(COMMAND_PREFIX.length());

        CommandSender senderUser = new CommandSender(eventUser.getPlatform(), eventUser.isBot(), eventUser.getUserId(), eventUser.getUsername(),
                event.getGroupId(), msgId, eventUser.getData(), event.getMessage().getMentionedUsers(), eventUser.getRole());

        boolean executed = commandMap.dispatch(senderUser, commandContent);
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            // command not found
        }
    }

    @EventHandler
    public void onOfficialC2CCommand(OfficialC2CMessageCreateEvent event) {
        String userInput = event.getMessage().getContent().trim();

        if (!userInput.startsWith(COMMAND_PREFIX)) {
            return;
        }

        User eventUser = event.getUser();
        String msgId = event.getMessage().getMessageId();
        String commandContent = userInput.substring(COMMAND_PREFIX.length());

        CommandSender senderUser = new CommandSender(eventUser.getPlatform(), eventUser.isBot(), eventUser.getUserId(), eventUser.getUsername(),
                null, msgId, eventUser.getData(), event.getMessage().getMentionedUsers(), eventUser.getRole());

        boolean executed = commandMap.dispatch(senderUser, commandContent);
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            senderUser.sendMessage("未知的命令，请使用 /help 查看可用指令列表，如有任何问题，请使用 /feedback 命令反馈给开发者");
        }
    }

    @EventHandler
    public void onOfficialGroupAtMessageCreate(OfficialGroupAtMessageCreateEvent event) {
        String userInput = event.getMessage().getContent().trim();

        if (!userInput.startsWith(COMMAND_PREFIX)) {
            return;
        }

        User eventUser = event.getUser();
        String msgId = event.getMessage().getMessageId();
        String commandContent = userInput.substring(COMMAND_PREFIX.length());

        CommandSender senderUser = new CommandSender(eventUser.getPlatform(), eventUser.isBot(), eventUser.getUserId(), eventUser.getUsername(),
                event.getGroupId(), msgId, eventUser.getData(), event.getMessage().getMentionedUsers(), eventUser.getRole());

        boolean executed = commandMap.dispatch(senderUser, commandContent);
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            senderUser.sendMessage("未知的命令，请使用 /help 查看可用指令列表，如有任何问题，请使用 /feedback 命令反馈给开发者");
        }
    }

    @EventHandler
    public void onOfficialGroupMessageCreate(OfficialGroupMessageCreateEvent event) {
        String userInput = event.getMessage().getContent().trim();

        if (!userInput.startsWith(COMMAND_PREFIX)) {
            if (event.isAtBot()) {
                userInput = userInput.replaceFirst("^<@[^>]+>\\s*", "").trim();
            } else {
                return;
            }
        }

        if (userInput.trim().isEmpty()) return;

        User eventUser = event.getUser();
        String msgId = event.getMessage().getMessageId();
        String commandContent = userInput.substring(COMMAND_PREFIX.length());

        CommandSender senderUser = new CommandSender(eventUser.getPlatform(), eventUser.isBot(), eventUser.getUserId(), eventUser.getUsername(),
                event.getGroupId(), msgId, eventUser.getData(), event.getMessage().getMentionedUsers(), eventUser.getRole());

        boolean executed = commandMap.dispatch(senderUser, commandContent);
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            // senderUser.sendMessage("未知的命令，请使用 /help 查看可用指令列表，如有任何问题，请使用 /feedback 命令反馈给开发者");
        }
    }
}
