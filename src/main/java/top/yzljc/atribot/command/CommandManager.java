package top.yzljc.atribot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.config.ConfigFile;
import top.yzljc.atribot.event.Author;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.GroupMessageEvent;
import top.yzljc.atribot.event.impl.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.impl.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.event.impl.OfficialC2CMessageEvent;
import top.yzljc.atribot.functions.official.permission.C2CList;
import top.yzljc.atribot.utils.BotRuntimeData;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandManager implements Listener {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);
    private static final String COMMAND_FILE = ConfigFile.ATRIBOT.getFileName();
    private static final String COMMAND_PREFIX = Config.getInstance().getCommandPrefix();
    private static final String DEBUG_SUFFIX = Config.getInstance().getDebugCommandSuffix();
    private static final List<Long> adminUids = Config.getInstance().getNapcatAdminUins();
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
    public void processCommand(GroupMessageEvent event) {
        long userId = event.getUserId();
        if (userId == event.getSelfId()) {
            return;
        }

        String rawMessage = event.getRawMessage().trim();
        if (!rawMessage.startsWith(COMMAND_PREFIX)) {
            return;
        }

        long groupId = event.getGroupId();
        boolean isAdmin = adminUids.contains(userId);
        boolean isDebug = false;
        long messageId = event.getMessageId();

        String commandContent = rawMessage.substring(COMMAND_PREFIX.length());

        if (isAdmin && rawMessage.endsWith(DEBUG_SUFFIX)) {
            isDebug = true;
            commandContent = commandContent.substring(0, commandContent.length() - DEBUG_SUFFIX.length()).trim();
        }

        CommandSender sender = CommandSender.of(userId, groupId, isAdmin, isDebug, messageId, "0", event.getMentions());

        boolean executed = commandMap.dispatch(sender, commandContent, "0");
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            // command not found
        }
    }

    @EventHandler
    public void onOfficialPrivateCommand(OfficialC2CMessageEvent event) {
        String rawMessage = event.getContent().trim();
        if (!rawMessage.startsWith(COMMAND_PREFIX)) {
            return;
        }

        Author author = event.getAuthor();

        boolean isAdmin = C2CList.isAdmin(author.getUnionOpenId());
        boolean isDebug = false;
        String msgId = event.getMsgId();

        String commandContent = rawMessage.substring(COMMAND_PREFIX.length());

        if (isAdmin && rawMessage.endsWith(DEBUG_SUFFIX)) {
            isDebug = true;
            commandContent = commandContent.substring(0, commandContent.length() - DEBUG_SUFFIX.length()).trim();
        }

        CommandSender sender = CommandSender.of(author.getUnionOpenId(), isAdmin, isDebug, msgId, author, "1");

        boolean executed = commandMap.dispatch(sender, commandContent, "1");
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            sender.replyText("1", "未知的命令，请使用 /help 查看可用指令列表，如有任何问题，请使用 /feedback 命令反馈给开发者");
        }
    }

    @EventHandler
    public void onOfficialGroupAtMessageCreate(OfficialGroupAtMessageCreateEvent event) {
        String rawMessage = event.getContent().trim();
        if (!rawMessage.startsWith(COMMAND_PREFIX)) {
            return;
        }

        Author author = event.getAuthor();

        boolean isAdmin = C2CList.isAdmin(author.getUnionOpenId());
        boolean isDebug = false;
        String msgId = event.getMsgId();
        String groupOpenId = event.getGroupOpenId();

        String commandContent = rawMessage.substring(COMMAND_PREFIX.length());

        if (isAdmin && rawMessage.endsWith(DEBUG_SUFFIX)) {
            isDebug = true;
            commandContent = commandContent.substring(0, commandContent.length() - DEBUG_SUFFIX.length()).trim();
        }

        CommandSender sender = CommandSender.of(author.getUnionOpenId(), groupOpenId, isAdmin, isDebug, msgId, author, "2");

        boolean executed = commandMap.dispatch(sender, commandContent, "2");
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            sender.replyText("2", "未知的命令，请使用 /help 查看可用指令列表，如有任何问题，请使用 /feedback 命令反馈给开发者");
        }
    }

    @EventHandler
    public void onOfficialGroupMessageCreate(OfficialGroupMessageCreateEvent event) {
        String rawMessage = event.getContent().trim();
        boolean isAtAndCommandMessage = rawMessage.contains("/") && event.isAtBotMessage();
        if (!(rawMessage.startsWith(COMMAND_PREFIX) || isAtAndCommandMessage)) {
            return;
        }

        if (isAtAndCommandMessage) {
            rawMessage = rawMessage.replaceFirst("^<@[^>]+>\\s*", "").trim();
        }

        Author author = event.getAuthor();

        boolean isAdmin = C2CList.isAdmin(author.getUnionOpenId());
        boolean isDebug = false;
        String msgId = event.getMessageId();
        String groupOpenId = event.getGroupOpenId();

        String commandContent = rawMessage.substring(COMMAND_PREFIX.length());

        if (isAdmin && rawMessage.endsWith(DEBUG_SUFFIX)) {
            isDebug = true;
            commandContent = commandContent.substring(0, commandContent.length() - DEBUG_SUFFIX.length()).trim();
        }

        CommandSender sender = CommandSender.of(author.getUnionOpenId(), groupOpenId, isAdmin, isDebug, msgId, author, "2");

        boolean executed = commandMap.dispatch(sender, commandContent, "2");
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            // sender.replyText("2", "未知的命令，请使用 /help 查看可用指令列表，如有任何问题，请使用 /feedback 命令反馈给开发者");
        }
    }
}
