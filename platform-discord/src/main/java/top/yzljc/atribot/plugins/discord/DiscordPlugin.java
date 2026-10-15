package top.yzljc.atribot.plugins.discord;

import java.nio.file.Files;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import top.yzljc.atribot.plugin.AtriPlugin;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.platform.discord.DiscordManager;
import top.yzljc.atribot.command.impl.DiscordSenderImpl;

/**
 * @Author YZ_Ljc_
 * @ClassName DiscordPlugin
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugins.discord
 */
public final class DiscordPlugin extends AtriPlugin implements Listener {
    private static volatile String apiBaseUrl = "https://discord.com/api/v10";
    public static String apiBaseUrl() { return apiBaseUrl; }

    @Override
    public void onEnable() throws Exception {
        Map<?, ?> settings = readSettings();
        apiBaseUrl = value(settings, "api-base-url", "https://discord.com/api/v10");
        DiscordCommands commands = new DiscordCommands();
        for (var definition : getContext().getDescription().commands()) getCommand(definition.command().name()).setSlashExecutor(commands);
        var manager = new DiscordManager(apiBaseUrl, required(settings, "bot-token"), Integer.parseInt(value(settings, "intents", "37377")));
        getContext().manage((AutoCloseable) manager::stop);
        getContext().registerEvents(this);
        manager.start();
    }

    @EventHandler
    public void onSlashCommand(DiscordSlashCommandEvent event) {
        getContext().getLogger().log(System.Logger.Level.INFO, "[Discord] slash command: guild=" + event.getGuildId()
                + ", channel=" + event.getChannelId() + ", user=" + event.getUser().getUsername() + ", /" + event.getCommandName());
        var sender = new DiscordSenderImpl(event.getUser(), event.getApplicationId(), event.getInteractionId(), event.getToken());
        if (!CommandManager.dispatchSlash(sender, event.getCommandName(), event.getArgs())) sender.sendEphemeralMessage("未知的指令。");
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
