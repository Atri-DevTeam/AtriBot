package top.yzljc.atribot.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName PluginManager
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 */
public final class PluginManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);
    private final Path directory;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final List<Entry> enabledOrder = new ArrayList<>();
    private boolean started;
    private boolean closed;
    private Thread shutdownHook;

    public PluginManager(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public synchronized void loadPlugins() {
        if (started || closed) throw new IllegalStateException("插件仅支持启动时加载，不支持 reload");
        started = true;
        try {
            Files.createDirectories(directory);
            List<Path> jars;
            try (var files = Files.list(directory)) {
                jars = files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .sorted().toList();
            }
            Set<String> duplicateNames = new HashSet<>();
            for (Path jar : jars) {
                try {
                    PluginDescription description = PluginDescription.read(jar);
                    Entry entry = new Entry(jar, description);
                    if (entries.putIfAbsent(description.name(), entry) != null) duplicateNames.add(description.name());
                } catch (Exception | LinkageError failure) {
                    log.error("跳过无法读取的插件 {}: {}", jar.getFileName(), failure.getMessage(), failure);
                }
            }
            for (String name : duplicateNames) fail(entries.get(name), new IllegalArgumentException("发现重名插件，请保留一个 JAR: " + name));
            if (!entries.isEmpty()) {
                shutdownHook = new Thread(this::close, "atri-plugins-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            }
            for (Entry entry : entries.values().stream().sorted(Comparator.comparing(item -> item.description.name())).toList()) {
                enable(entry, new HashSet<>());
            }
            log.info("插件加载完成: {} 个已启用，{} 个失败，目录 {}", enabledOrder.size(),
                    entries.size() - enabledOrder.size(), directory);
        } catch (IOException | SecurityException failure) {
            log.error("无法读取插件目录 {}，原有功能继续运行", directory, failure);
        }
    }

    public Optional<AtriPlugin> getPlugin(String name) {
        if (name == null) return Optional.empty();
        Entry entry = entries.get(name);
        AtriPlugin plugin = entry == null ? null : entry.plugin;
        return plugin != null && entry.state == State.ENABLED ? Optional.of(plugin) : Optional.empty();
    }

    public List<PluginInfo> getPlugins() {
        return entries.values().stream().sorted(Comparator.comparing(entry -> entry.description.name()))
                .map(entry -> new PluginInfo(entry.description, entry.state, entry.error)).toList();
    }

    private boolean enable(Entry entry, Set<String> visiting) {
        if (entry.state != State.DISCOVERED) return entry.state == State.ENABLED;
        String name = entry.description.name();
        if (!visiting.add(name)) {
            fail(entry, new IllegalArgumentException("插件依赖存在循环: " + name));
            return false;
        }
        try {
            List<PluginClassLoader> dependencies = new ArrayList<>();
            for (String dependencyName : entry.description.depend()) {
                Entry dependency = entries.get(dependencyName);
                if (dependency == null || !enable(dependency, visiting)) {
                    throw new IllegalArgumentException("缺少依赖或依赖启用失败: " + dependencyName);
                }
                dependencies.add(dependency.loader);
            }
            Path dataDirectory = directory.resolve(name);
            Files.createDirectories(dataDirectory);
            if (!dataDirectory.toRealPath().startsWith(directory.toRealPath())) {
                throw new IOException("插件数据目录不能指向插件目录之外");
            }
            entry.loader = new PluginClassLoader(entry.jar.toUri().toURL(), dependencies);
            entry.context = new PluginContext(this, entry.description, dataDirectory, entry.loader);
            withClassLoader(entry.loader, () -> {
                Class<?> main = Class.forName(entry.description.main(), true, entry.loader);
                if (main.getClassLoader() != entry.loader || !AtriPlugin.class.isAssignableFrom(main)) {
                    throw new IllegalArgumentException("main 必须是当前插件 JAR 内继承 AtriPlugin 的类");
                }
                entry.plugin = main.asSubclass(AtriPlugin.class).getConstructor().newInstance();
                entry.plugin.initialize(entry.context);
                entry.context.registerCommands(entry.plugin);
                entry.plugin.onLoad();
                entry.plugin.onEnable();
            });
            entry.state = State.ENABLED;
            entry.context.enableCommands();
            enabledOrder.add(entry);
            log.info("已启用插件 {} v{}", name, entry.description.version());
            return true;
        } catch (Throwable failure) {
            rethrowFatal(failure);
            fail(entry, failure);
            release(entry);
            return false;
        } finally {
            visiting.remove(name);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (int i = enabledOrder.size() - 1; i >= 0; i--) {
            Entry entry = enabledOrder.get(i);
            entry.state = State.DISABLED;
            release(entry);
        }
        enabledOrder.clear();
        if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException | SecurityException ignored) {
            }
        }
    }

    private void release(Entry entry) {
        if (entry.context != null) entry.context.disableCommands();
        try {
            if (entry.plugin != null) withClassLoader(entry.loader, entry.plugin::onDisable);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            log.error("插件 {} 停用回调失败", entry.description.name(), failure);
        } finally {
            if (entry.context != null) entry.context.close();
            if (entry.loader != null) {
                try {
                    entry.loader.close();
                } catch (IOException failure) {
                    log.warn("插件 {} JAR 句柄关闭失败", entry.description.name(), failure);
                }
            }
            entry.plugin = null;
            entry.context = null;
            entry.loader = null;
        }
    }

    private void fail(Entry entry, Throwable failure) {
        entry.state = State.FAILED;
        Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null ? failure.getCause() : failure;
        entry.error = cause.toString();
        log.error("插件 {} 加载失败，原有功能与无依赖关系的插件继续运行", entry.description.name(), cause);
    }

    private static void withClassLoader(ClassLoader loader, Callback callback) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            callback.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @SuppressWarnings("removal")
    static void rethrowFatal(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof VirtualMachineError fatal) throw fatal;
        if (cause instanceof ThreadDeath fatal) throw fatal;
    }

    public enum State { DISCOVERED, ENABLED, FAILED, DISABLED }

    public record PluginInfo(PluginDescription description, State state, String error) {}

    @FunctionalInterface
    private interface Callback { void run() throws Exception; }

    private static final class Entry {
        private final Path jar;
        private final PluginDescription description;
        private volatile State state = State.DISCOVERED;
        private volatile String error;
        private PluginClassLoader loader;
        private PluginContext context;
        private volatile AtriPlugin plugin;

        private Entry(Path jar, PluginDescription description) {
            this.jar = jar;
            this.description = description;
        }
    }
}
