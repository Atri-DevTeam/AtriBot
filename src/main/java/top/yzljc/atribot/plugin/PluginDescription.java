package top.yzljc.atribot.plugin;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * @Author YZ_Ljc_
 * @ClassName PluginDescription
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 */
public record PluginDescription(String name, String version, String main, int apiVersion, List<String> depend,
                                List<PluginCommandDefinition> commands) {
    public PluginDescription {
        depend = List.copyOf(depend);
        commands = List.copyOf(commands);
    }

    public PluginDescription(String name, String version, String main, int apiVersion, List<String> depend) {
        this(name, version, main, apiVersion, depend, List.of());
    }

    static PluginDescription read(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            var entry = jar.getJarEntry("plugin.yml");
            if (entry == null) throw new IllegalArgumentException("JAR 根目录缺少 plugin.yml");
            byte[] bytes;
            try (var input = jar.getInputStream(entry)) {
                bytes = input.readNBytes(65537);
            }
            if (bytes.length > 65536) throw new IllegalArgumentException("plugin.yml 不能超过 64 KiB");
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(10);
            options.setCodePointLimit(65536);
            Object parsed = new Yaml(new SafeConstructor(options)).load(new String(bytes, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> data)) throw new IllegalArgumentException("plugin.yml 必须是对象");
            String name = identifier(required(data, "name"));
            String version = required(data, "version");
            String main = required(data, "main");
            if (!main.matches("[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)+")) {
                throw new IllegalArgumentException("main 必须是插件入口的完整类名");
            }
            if (!"1".equals(String.valueOf(data.get("api-version")))) {
                throw new IllegalArgumentException("当前仅支持 api-version: 1");
            }
            List<String> dependencies = new ArrayList<>();
            Object rawDependencies = data.get("depend");
            if (rawDependencies != null) {
                if (!(rawDependencies instanceof List<?> list)) throw new IllegalArgumentException("depend 必须是插件名列表");
                for (Object value : list) {
                    if (!(value instanceof String text)) throw new IllegalArgumentException("依赖名称必须是文本");
                    String dependency = identifier(text);
                    if (dependency.equals(name) || dependencies.contains(dependency)) {
                        throw new IllegalArgumentException("不能依赖自己或重复声明依赖: " + dependency);
                    }
                    dependencies.add(dependency);
                }
            }
            return new PluginDescription(name, version, main, 1, dependencies, PluginCommandDefinition.parse(data.get("commands")));
        }
    }

    private static String required(Map<?, ?> data, String key) {
        if (!(data.get(key) instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " 必须是非空文本（版本号请加引号）");
        }
        return text.trim();
    }

    private static String identifier(String name) {
        if (!name.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("插件名须以小写字母开头，仅包含小写字母、数字、下划线、连字符，最长 64 字符");
        }
        return name;
    }
}
