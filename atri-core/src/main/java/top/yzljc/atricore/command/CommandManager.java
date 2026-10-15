package top.yzljc.atricore.command;

import top.yzljc.atricore.AtriProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import top.yzljc.atricore.command.impl.NapcatSenderImpl;
import top.yzljc.atricore.command.impl.QQGuildSenderImpl;
import top.yzljc.atricore.command.impl.QQSenderImpl;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.*;
import top.yzljc.atricore.platform.Platform;

import java.io.InputStream;
import java.util.*;

/**
 * 指令声明加载与平台消息分发入口，首次使用前必须注册 AtriProvider
 *
 * @Author YZ_Ljc_
 * @ClassName CommandManager
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
public class CommandManager implements Listener {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private static final CommandMap commandMap = new CommandMap();
    private static volatile List<CommandDefinition> registeredDefinitions = List.of();

    static {
        reload();
    }

    /**
     * 按名称或别名查询通用指令
     *
     * @param name 指令名称，不包含前缀
     * @return 匹配的指令，未注册时返回 null
     */
    public static CommandFeature getCommand(String name) {
        return (CommandFeature) commandMap.getCommand(name);
    }

    /**
     * 按平台查询指令，优先使用该平台的限定指令
     *
     * @param name 指令名称，不包含前缀
     * @param platform 来源平台，无平台来源可为 null
     * @return 匹配且支持该平台的指令，未找到时返回 null
     */
    public static CommandFeature getCommand(String name, Platform platform) {
        return (CommandFeature) commandMap.getCommand(name, platform);
    }

    /**
     * 列出指定平台当前可用的指令
     *
     * @param platform 来源平台，无平台来源可为 null
     * @return 按有效指令去重后的列表
     */
    public static List<CommandFeature> getCommands(Platform platform) {
        return commandMap.getCommands(platform);
    }

    /**
     * 同步分发已经解析的指令行
     *
     * @param sender 指令发送者
     * @param commandLine 已去除前缀或完成触发词映射的指令行
     * @return 找到分发目标时返回 true，包括取消、业务禁用及执行异常；未匹配时返回 false
     */
    public static boolean dispatch(CommandSender sender, String commandLine) {
        return commandMap.dispatch(sender, commandLine);
    }

    /**
     * 按当前配置和发送者平台解析文本，不执行指令
     *
     * @param sender 指令发送者
     * @param input 已去除平台提及标记的文本，可为 null
     * @return 可交给 dispatch 的指令行，普通消息返回 null
     */
    public static String resolveCommandLine(CommandSender sender, String input) {
        return commandMap.resolveCommandLine(input, AtriProvider.getInstance().getSettings().getCommandPrefix(), sender.getPlatform());
    }

    /**
     * 使用当前指令前缀和触发词索引判断文本是否为指令输入
     *
     * @param input 已去除平台提及标记的消息文本，允许为 null
     * @return 包含显式前缀或命中已注册无前缀触发词时返回 true，不代表指令存在或用户有执行权限
     */
    public static boolean isCommand(String input) {
        return commandMap.isCommand(input, AtriProvider.getInstance().getSettings().getCommandPrefix());
    }

    /**
     * 重新加载类路径中的指令声明，按主指令名保留已绑定的执行器
     *
     * @throws IllegalStateException 指令声明读取或解析失败
     * @throws IllegalArgumentException 无前缀触发词等注册信息发生冲突
     */
    public static synchronized void reload() {
        Map<String, CommandExecutor> executors = commandMap.snapshotExecutors();
        List<CommandDefinition> definitions = loadDefinitions();

        List<CommandFeature> commands = new ArrayList<>();
        for (CommandDefinition definition : definitions) {
            CommandFeature command = new CommandFeature(definition);
            CommandExecutor executor = executors.get(definition.name().toLowerCase(Locale.ROOT));
            if (executor != null) {
                command.setExecutor(executor);
            }
            commands.add(command);
        }
        commandMap.replaceCoreCommands(commands);
        registeredDefinitions = List.copyOf(definitions);

        log.info("命令配置已加载，共 {} 个命令", definitions.size());
    }

    /**
     * 获取最近一次加载的主指令声明
     *
     * @return 不可修改的声明列表，不包含动态插件指令
     */
    public static List<CommandDefinition> getDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 注册指定所有者的扩展指令
     *
     * @param owner 指令所有者，用于构造 owner:name 限定名称
     * @param commands 本次注册的全部指令
     * @throws IllegalStateException 该所有者已注册
     * @throws IllegalArgumentException 平台标签或无前缀触发词冲突
     */
    public static void registerPluginCommands(String owner, List<? extends ScopedCommand> commands) {
        commandMap.registerPluginCommands(owner, commands);
    }

    /**
     * 移除指定所有者的指令，仅在清单与注册时相同时生效
     *
     * @param owner 注册时使用的所有者
     * @param commands 注册时的指令清单
     */
    public static void unregisterPluginCommands(String owner, List<? extends ScopedCommand> commands) {
        commandMap.unregisterPluginCommands(owner, commands);
    }

    private static List<CommandDefinition> loadDefinitions() {
        String commandFile = AtriProvider.getInstance().getBotCommand().commandResource();
        try (InputStream in = CommandManager.class.getClassLoader().getResourceAsStream(commandFile)) {
            if (in == null) {
                log.warn("未找到命令配置资源 {}", commandFile);
                return List.of();
            }
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            if (data == null) {
                return List.of();
            }

            Object commandsObj = data.get("commands");
            if (!(commandsObj instanceof Map<?, ?> rawCommands)) {
                log.warn("命令配置 {} 中缺少 commands 节点", commandFile);
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
            log.error("加载命令配置 {} 失败", commandFile, e);
            throw new IllegalStateException("加载命令配置失败: " + commandFile, e);
        }
    }

    private void dispatchTextCommand(CommandSender sender, String input, boolean replyUnknown) {
        String commandLine = commandMap.resolveCommandLine(input, AtriProvider.getInstance().getSettings().getCommandPrefix());
        if (commandLine == null) return;
        boolean executed = commandMap.dispatch(sender, commandLine);
        AtriProvider.getInstance().getLogRecorder().callCommandExecuted();
        if (!executed && replyUnknown) sender.sendMessage(AtriProvider.getInstance().getBotCommand().unknownCommandMessage());
    }

    @EventHandler
    public void processCommand(NapcatGroupMessageEvent event) {
        if (event.getUser().isBot()) return;
        String userInput = event.getMessage().getContent().trim();

        var eventUser = event.getUser();

        if (Objects.equals(eventUser.getUserId(), AtriProvider.getInstance().getSettings().getNapcatBotUin())) {
            return;
        }

        NapcatCommandSender senderUser = new NapcatSenderImpl(eventUser, event.getGroupId(), event.getMessage());
        dispatchTextCommand(senderUser, userInput, false);
    }

    @EventHandler
    public void onNapcatPrivateCommand(NapcatPrivateMessageEvent event) {
        if (event.getUser().isBot()
                || Objects.equals(event.getUser().getUserId(), AtriProvider.getInstance().getSettings().getNapcatBotUin())) return;
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

        if (event.isAtBot() && !userInput.startsWith(AtriProvider.getInstance().getSettings().getCommandPrefix())) {
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

}
