package top.yzljc.atribot.plugin;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.LoginService;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.Listener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @Author YZ_Ljc_
 * @ClassName PluginContext
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 */
public final class PluginContext {
    private final PluginManager manager;
    private final PluginDescription description;
    private final Path dataDirectory;
    private final PluginClassLoader classLoader;
    private final System.Logger logger;
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private ScheduledThreadPoolExecutor scheduler;
    private boolean closed;
    private Map<String, PluginCommand> commands = Map.of();
    private List<PluginCommand> registeredCommands = List.of();

    PluginContext(PluginManager manager, PluginDescription description, Path dataDirectory, PluginClassLoader classLoader) {
        this.manager = manager;
        this.description = description;
        this.dataDirectory = dataDirectory;
        this.classLoader = classLoader;
        this.logger = new PluginLogger(description.name());
    }

    public PluginDescription getDescription() { return description; }

    public Path getDataDirectory() { return dataDirectory; }

    public System.Logger getLogger() { return logger; }

    public Atri getBot() { return Atri.getInstance(); }

    /** 与宿主 /login 命令共享的验证服务；生命周期由宿主管理。 */
    public synchronized LoginService getLoginService() {
        ensureOpen();
        return LoginService.getInstance();
    }

    public Connection getConnection() throws SQLException { return DatabaseManager.getConnection(); }

    public Optional<AtriPlugin> getPlugin(String name) { return manager.getPlugin(name); }

    public synchronized PluginCommand getCommand(String name) {
        if (name == null) return null;
        String label = name.toLowerCase(Locale.ROOT);
        String prefix = description.name() + ":";
        if (label.startsWith(prefix)) label = label.substring(prefix.length());
        return commands.get(label);
    }

    synchronized void registerCommands(AtriPlugin plugin) {
        ensureOpen();
        if (description.commands().isEmpty()) return;
        Map<String, PluginCommand> labels = new LinkedHashMap<>();
        registeredCommands = description.commands().stream().map(definition -> new PluginCommand(plugin, definition)).toList();
        for (PluginCommand command : registeredCommands) {
            labels.put(command.getName(), command);
            command.getAliases().forEach(alias -> labels.put(alias, command));
        }
        commands = Map.copyOf(labels);
        CommandManager.registerPluginCommands(description.name(), registeredCommands);
    }

    synchronized void enableCommands() {
        ensureOpen();
        registeredCommands.forEach(command -> command.setActive(true));
    }

    synchronized void disableCommands() {
        registeredCommands.forEach(command -> command.setActive(false));
        if (!registeredCommands.isEmpty()) {
            CommandManager.unregisterPluginCommands(description.name(), registeredCommands);
        }
    }

    public synchronized void registerEvents(Listener listener) {
        ensureOpen();
        Objects.requireNonNull(listener, "listener");
        try {
            EventManager.getInstance().registerEvents(listener);
            resources.push(() -> EventManager.getInstance().unregisterEvents(listener));
        } catch (RuntimeException | LinkageError failure) {
            EventManager.getInstance().unregisterEvents(listener);
            throw failure;
        }
    }

    public synchronized <T extends AutoCloseable> T manage(T resource) {
        ensureOpen();
        resources.push(Objects.requireNonNull(resource, "resource"));
        return resource;
    }

    public synchronized ScheduledExecutorService getScheduler() {
        ensureOpen();
        if (scheduler == null) {
            scheduler = new ScheduledThreadPoolExecutor(1, runnable -> Thread.ofPlatform()
                    .daemon(true).name("plugin-" + description.name() + "-scheduler")
                    .unstarted(() -> {
                        Thread.currentThread().setContextClassLoader(classLoader);
                        runnable.run();
                    }));
            scheduler.setRemoveOnCancelPolicy(true);
            scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        }
        return scheduler;
    }

    public synchronized Path saveDefaultConfig() throws IOException {
        ensureOpen();
        Path target = dataDirectory.resolve("config.yml");
        if (Files.exists(target)) return target;
        var resource = classLoader.findResource("config.yml");
        if (resource == null) throw new IOException("插件 JAR 中没有 config.yml");
        var connection = resource.openConnection();
        connection.setUseCaches(false);
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, target);
        }
        return target;
    }

    void close() {
        Deque<AutoCloseable> pending;
        synchronized (this) {
            if (closed) return;
            closed = true;
            disableCommands();
            if (scheduler != null) scheduler.shutdownNow();
            pending = new ArrayDeque<>(resources);
            resources.clear();
        }
        for (AutoCloseable resource : pending) {
            try {
                resource.close();
            } catch (Throwable failure) {
                PluginManager.rethrowFatal(failure);
                logger.log(System.Logger.Level.ERROR, "插件资源释放失败", failure);
            }
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("插件已经停用，不能注册新资源");
    }
}
