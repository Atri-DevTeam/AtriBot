package top.yzljc.atribot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import top.yzljc.atribot.command.impl.DiscordSenderImpl;
import top.yzljc.atribot.command.impl.NapcatSenderImpl;
import top.yzljc.atribot.command.impl.QQGuildSenderImpl;
import top.yzljc.atribot.command.impl.QQSenderImpl;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.i18n.I18N;
import top.yzljc.atribot.plugin.PluginCommand;
import top.yzljc.atribot.platform.kook.KookMessage;
import top.yzljc.atribot.platform.kook.KookUser;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;

import java.io.InputStream;
import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandManager
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public class CommandManager implements Listener {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);
    private static final String COMMAND_FILE = Properties.ATRIBOT;
    private static final String COMMAND_PREFIX = Config.getInstance().getCommandPrefix();

    private static final CommandMap commandMap = new CommandMap();
    private static volatile List<CommandDefinition> registeredDefinitions = List.of();

    static {
        reload();
    }

    public static CommandFeature getCommand(String name) {
        return (CommandFeature) commandMap.getCommand(name);
    }

    /**
     * 使用当前指令前缀和触发词索引判断文本是否为指令输入
     *
     * @param input 已去除平台提及标记的消息文本，允许为 null
     * @return 包含显式前缀或命中已注册无前缀触发词时返回 true，不代表指令存在或用户有执行权限
     */
    public static boolean isCommand(String input) {
        return commandMap.isCommand(input, COMMAND_PREFIX);
    }

    public static synchronized void reload() {
        Map<String, CommandExecutor> executors = commandMap.snapshotExecutors();
        Map<String, SlashCommandExecutor> slashExecutors = commandMap.snapshotSlashExecutors();
        List<CommandDefinition> definitions = loadDefinitions();

        List<CommandFeature> commands = new ArrayList<>();
        for (CommandDefinition definition : definitions) {
            CommandFeature command = new CommandFeature(definition);
            CommandExecutor executor = executors.get(definition.name().toLowerCase(Locale.ROOT));
            if (executor != null) {
                command.setExecutor(executor);
            }
            command.setSlashExecutor(slashExecutors.get(definition.name().toLowerCase(Locale.ROOT)));
            commands.add(command);
        }
        commandMap.replaceCoreCommands(commands);
        registeredDefinitions = List.copyOf(definitions);

        log.info("命令配置已加载，共 {} 个命令", definitions.size());
    }

    public static List<CommandDefinition> getDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    public static void registerPluginCommands(String owner, List<PluginCommand> commands) {
        commandMap.registerPluginCommands(owner, commands);
    }

    public static void unregisterPluginCommands(String owner, List<PluginCommand> commands) {
        commandMap.unregisterPluginCommands(owner, commands);
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
            throw new IllegalStateException("加载命令配置失败: " + COMMAND_FILE, e);
        }
    }

    private void dispatchTextCommand(CommandSender sender, String input, boolean replyUnknown) {
        String commandLine = commandMap.resolveCommandLine(input, COMMAND_PREFIX);
        if (commandLine == null) return;
        boolean executed = commandMap.dispatch(sender, commandLine);
        BotRuntimeData.callCommandExecuted();
        if (!executed && replyUnknown) sender.sendMessage(I18N.text("command.unknown"));
    }

    @EventHandler
    public void processCommand(NapcatGroupMessageEvent event) {
        if (event.getUser().isBot()) return;
        String userInput = event.getMessage().getContent().trim();

        var eventUser = event.getUser();

        if (Objects.equals(eventUser.getUserId(), Config.getInstance().getNapcatBotUin())) {
            return;
        }

        NapcatCommandSender senderUser = new NapcatSenderImpl(eventUser, event.getGroupId(), event.getMessage());
        dispatchTextCommand(senderUser, userInput, false);
    }

    @EventHandler
    public void onNapcatPrivateCommand(NapcatPrivateMessageEvent event) {
        if (event.getUser().isBot()
                || Objects.equals(event.getUser().getUserId(), Config.getInstance().getNapcatBotUin())) return;
        var sender = new NapcatSenderImpl(event.getUser(), null, event.getMessage());
        dispatchTextCommand(sender, event.getMessage().getContent(), false);
    }

    @EventHandler
    public void onOfficialC2CCommand(OfficialC2CMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        String userInput = event.getMessage().getContent().trim();
        var eventUser = event.getUser();
        QQCommandSender senderUser = new QQSenderImpl(eventUser, null, event.getMessage());
        dispatchTextCommand(senderUser, userInput, true);
    }

    @EventHandler
    public void onOfficialGroupAtMessageCreate(OfficialGroupAtMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        String userInput = event.getMessage().getContent().trim();

        var eventUser = event.getUser();
        QQCommandSender senderUser = new QQSenderImpl(eventUser, event.getGroupId(), event.getMessage());
        dispatchTextCommand(senderUser, userInput, true);
    }

    @EventHandler
    public void onOfficialGroupMessageCreate(OfficialGroupMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        String userInput = event.getMessage().getContent().trim();

        if (event.isAtBot() && !userInput.startsWith(COMMAND_PREFIX)) {
            userInput = userInput.replaceFirst("^<@[^>]+>\\s*", "").trim();
        }
        var eventUser = event.getUser();
        QQCommandSender senderUser = new QQSenderImpl(eventUser, event.getGroupId(), event.getMessage());
        dispatchTextCommand(senderUser, userInput, event.isAtBot());
    }
    @EventHandler
    public void onOfficialGuildAtMessageCreate(OfficialGuildAtMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        String userInput = event.getMessage().getContent().trim();
        String revPrefixContent = userInput.replaceFirst("^<@[^>]+>\\s*", "").trim();
        var channelUser = event.getUser();
        var senderUser = new QQGuildSenderImpl(channelUser, event.getMessage(), event.getGuildId(), event.getChannelId(), event.getUserOpenId());
        dispatchTextCommand(senderUser, revPrefixContent, true);
    }

    @EventHandler
    public void onOfficialGuildDirectMessageCreate(OfficialGuildDirectMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        String userInput = event.getMessage().getContent().trim();
        String revPrefixContent = userInput.replaceFirst("^<@[^>]+>\\s*", "").trim();
        var channelUser = event.getUser();
        var senderUser = new QQGuildSenderImpl(channelUser, event.getMessage(), event.getGuildId(), event.getChannelId(), event.getUserOpenId());
        dispatchTextCommand(senderUser, revPrefixContent, true);
    }

    @EventHandler
    public void onDiscordSlashCommand(DiscordSlashCommandEvent event) {
        log.info("[Discord] slash command: guild={}, channel={}, user={}, /{}",
                event.getGuildId(),
                event.getChannelId(),
                event.getUser().getUsername(),
                event.getCommandName());

        CommandFeature command = getCommand(event.getCommandName());
        if (command == null) {
            log.warn("Discord slash command /{} is not registered in CommandManager", event.getCommandName());
            return;
        }

        SlashCommandExecutor slashExecutor = command.getSlashExecutor();
        if (slashExecutor == null) {
            log.warn("Discord slash command /{} has no SlashCommandExecutor", event.getCommandName());
            return;
        }

        DiscordCommandSender sender = new DiscordSenderImpl(
                event.getUser(),
                event.getApplicationId(),
                event.getInteractionId(),
                event.getToken()
        );
        slashExecutor.onCommand(sender, command, event.getCommandName(), event.getArgs());
    }

    @EventHandler
    public void onKookChannelMessage(KookChannelMessageCreateEvent event) {
        dispatchKookCommand(event.getUser(), event.getMessage(), event.getSender());
    }

    @EventHandler
    public void onKookDirectMessage(KookDirectMessageCreateEvent event) {
        dispatchKookCommand(event.getUser(), event.getMessage(), event.getSender());
    }

    private void dispatchKookCommand(KookUser user,
                                     KookMessage message,
                                     KookCommandSender sender) {
        if (user.isBot() || user.isBlocked() || !message.isText()) return;
        String content = message.getContent().trim();
        String commandLine = commandMap.resolveCommandLine(content, COMMAND_PREFIX);
        if (commandLine == null || commandLine.isBlank()) return;
        String scene = sender.getChannelId() == null ? "私信"
                : "服务器: " + sender.getGuildId() + ", 频道: " + sender.getChannelId();
        log.info("[KOOK] 用户 {} ({}) 使用指令: {}{} ({})",
                user.getUsername().replaceAll("[\\r\\n\\t]", " "), user.getUserId(),
                COMMAND_PREFIX, commandLine.replaceAll("\\s+", " "), scene);
        boolean executed = commandMap.dispatch(sender, commandLine);
        BotRuntimeData.callCommandExecuted();
        if (!executed) sender.sendMessage(I18N.text("command.unknown"));
    }
}
