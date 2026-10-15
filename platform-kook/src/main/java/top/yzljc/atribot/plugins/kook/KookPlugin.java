package top.yzljc.atribot.plugins.kook;

import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import top.yzljc.atribot.plugin.AtriPlugin;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.i18n.I18N;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;
import top.yzljc.atribot.platform.kook.*;
import top.yzljc.atribot.function.tasks.KookEventRecord;

/**
 * @Author YZ_Ljc_
 * @ClassName KookPlugin
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugins.kook
 */
public final class KookPlugin extends AtriPlugin implements Listener {
    @Override
    public void onEnable() throws Exception {
        Map<?, ?> settings = readSettings();
        Set<String> admins = new HashSet<>();
        if (settings.get("admin-ids") instanceof List<?> ids) ids.forEach(id -> admins.add(String.valueOf(id)));
        KookCommands commands = new KookCommands();
        for (var definition : getContext().getDescription().commands()) getCommand(definition.command().name()).setExecutor(commands);
        var manager = getContext().manage(new KookManager(new KookApiClient(value(settings, "api-base-url", "https://www.kookapp.cn/api/v3"),
                required(settings, "bot-token"), Duration.ofSeconds(20)), required(settings, "verify-token"),
                value(settings, "encrypt-key", ""), admins));
        getContext().registerEvents(this);
        getContext().registerEvents(new KookEventRecord());
        String route = getContext().registerPostRoute(value(settings, "webhook-path", "/webhook"), manager.getWebhookHandler()::handle);
        manager.start();
        getContext().getLogger().log(System.Logger.Level.INFO, "KOOK Webhook: POST " + route);
    }

    @EventHandler
    public void onChannelMessage(KookChannelMessageCreateEvent event) { dispatch(event.getUser(), event.getMessage(), event.getSender()); }

    @EventHandler
    public void onDirectMessage(KookDirectMessageCreateEvent event) { dispatch(event.getUser(), event.getMessage(), event.getSender()); }

    private void dispatch(KookUser user, KookMessage message, KookCommandSender sender) {
        if (user.isBot() || user.isBlocked() || !message.isText()) return;
        String commandLine = CommandManager.resolveCommandLine(sender, message.getContent().trim());
        if (commandLine == null || commandLine.isBlank()) return;
        String scene = sender.getChannelId() == null ? "私信" : "服务器: " + sender.getGuildId() + ", 频道: " + sender.getChannelId();
        getContext().getLogger().log(System.Logger.Level.INFO, "[KOOK] 用户 " + user.getUsername().replaceAll("[\\r\\n\\t]", " ")
                + " (" + user.getUserId() + ") 使用指令: " + Config.getInstance().getCommandPrefix()
                + commandLine.replaceAll("\\s+", " ") + " (" + scene + ")");
        boolean executed = CommandManager.dispatch(sender, commandLine);
        BotRuntimeData.callCommandExecuted();
        if (!executed) sender.sendMessage(I18N.text("command.unknown"));
    }

    private Map<?, ?> readSettings() throws Exception {
        var path = getContext().saveDefaultConfig();
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var input = Files.newInputStream(path)) {
            Object value = new Yaml(new SafeConstructor(options)).load(input);
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("config.yml 必须是对象");
            return map;
        }
    }
    private static String value(Map<?, ?> settings, String name, String fallback) {
        Object value = settings.get(name);
        return value == null ? fallback : String.valueOf(value).trim();
    }
    private static String required(Map<?, ?> settings, String name) {
        String value = value(settings, name, "");
        if (value.isBlank()) throw new IllegalArgumentException("请在插件 config.yml 中配置 " + name);
        return value;
    }
}
