package top.yzljc.qqbot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.event.impl.OfficialGroupAtMessageCreateEvent;
import top.yzljc.qqbot.event.impl.OfficialGroupMessageCreateEvent;
import top.yzljc.qqbot.event.impl.OfficialPrivateChatEvent;
import top.yzljc.qqbot.official.permission.PermissionGroup;
import top.yzljc.qqbot.official.permission.PermissionRole;
import top.yzljc.qqbot.utils.BotRuntimeData;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandManager implements Listener {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);
    private static final String COMMAND_FILE = ConfigFile.ATRIBOT.getFileName();
    private static final String COMMAND_PREFIX = Config.getInstance().getCommandPrefix();
    private static final String DEBUG_SUFFIX = Config.getInstance().getDebugCommandSuffix();
    private static final List<Long> adminUids = Config.getInstance().getAdminUids();
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

    public static void registerCommand(String name, String description, String usage, List<String> aliases, String featureKey) {
        if (usage == null) {
            usage = "/" + name;
        }
        if (aliases == null) {
            aliases = Collections.emptyList();
        }

        CommandFeature command = new CommandFeature(name, description, usage, aliases, featureKey, false);
        commandMap.register(FALLBACK_PREFIX, command);
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

        CommandSender sender = CommandSender.of(userId, groupId, isAdmin, isDebug, messageId);

        boolean executed = commandMap.dispatch(sender, commandContent, "0");
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            // command not found
        }
    }

    @EventHandler
    public void onOfficialPrivateCommand(OfficialPrivateChatEvent event) {
        String rawMessage = event.getContent().trim();
        if (!rawMessage.startsWith(COMMAND_PREFIX)) {
            return;
        }

        if (PermissionGroup.hasRole(event.getUnionOpenId(), PermissionRole.BLACKLIST)) return;

        boolean isAdmin = PermissionGroup.isAdmin(event.getUnionOpenId());
        boolean isDebug = false;
        String msgId = event.getMsgId();
        String userOpenId = event.getUnionOpenId();

        String commandContent = rawMessage.substring(COMMAND_PREFIX.length());

        CommandSender sender = CommandSender.of(userOpenId, isAdmin, isDebug, msgId);

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

        if (PermissionGroup.hasRole(event.getUnionOpenId(), PermissionRole.BLACKLIST)) return;

        boolean isAdmin = PermissionGroup.isAdmin(event.getUnionOpenId());
        boolean isDebug = false;
        String msgId = event.getMsgId();
        String userOpenId = event.getUnionOpenId();
        String groupOpenId = event.getGroupOpenId();

        String commandContent = rawMessage.substring(COMMAND_PREFIX.length());

        CommandSender sender = CommandSender.of(userOpenId, groupOpenId, isAdmin, isDebug, msgId);

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

        String user = event.getAuthor().getUnionOpenId();

        if (PermissionGroup.hasRole(user, PermissionRole.BLACKLIST)) return;

        boolean isAdmin = PermissionGroup.isAdmin(user);
        boolean isDebug = false;
        String msgId = event.getMessageId();
        String groupOpenId = event.getGroupOpenId();

        String commandContent = rawMessage.substring(COMMAND_PREFIX.length());

        CommandSender sender = CommandSender.of(user, groupOpenId, isAdmin, isDebug, msgId);

        boolean executed = commandMap.dispatch(sender, commandContent, "2");
        BotRuntimeData.callCommandExecuted();

        if (!executed) {
            sender.replyText("2", "未知的命令，请使用 /help 查看可用指令列表，如有任何问题，请使用 /feedback 命令反馈给开发者");
        }
    }
}
