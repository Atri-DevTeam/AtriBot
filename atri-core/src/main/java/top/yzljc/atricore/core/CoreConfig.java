package top.yzljc.atricore.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从运行目录加载框架配置，解析成功后替换当前配置对象
 *
 * @Author YZ_Ljc_
 * @ClassName CoreConfig
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.core
 */
public final class CoreConfig {
    public static final String FILE_NAME = "atri-core.yml";
    private final Path file;
    private volatile AtriSettings settings;

    private static class Holder {
        private static final CoreConfig INSTANCE = new CoreConfig(Path.of(FILE_NAME));
    }

    /**
     * 加载指定配置文件，文件不存在时从框架资源复制默认模板
     *
     * @param file 配置文件路径
     * @throws IllegalStateException 文件无法读取或配置格式无效
     */
    public CoreConfig(Path file) {
        this.file = file.toAbsolutePath().normalize();
        reload();
    }

    /**
     * 获取读取运行目录 atri-core.yml 的共享配置服务
     *
     * @return 共享配置服务，首次访问时加载配置
     */
    public static CoreConfig getInstance() { return Holder.INSTANCE; }
    /**
     * 获取最近一次成功加载的配置对象
     *
     * @return 当前配置对象，重载后应重新获取；修改该对象不会写回文件
     */
    public AtriSettings getSettings() { return settings; }
    /**
     * 获取配置文件路径
     *
     * @return 经过规范化的绝对路径
     */
    public Path getPath() { return file; }

    /**
     * 重新读取框架配置，全部解析成功后替换配置对象；失败时保留原配置
     *
     * @throws IllegalStateException 文件无法读取或配置格式无效
     */
    public synchronized void reload() {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                try (InputStream template = CoreConfig.class.getResourceAsStream("/" + FILE_NAME)) {
                    if (template == null) throw new IOException("默认配置资源不存在");
                    Files.copy(template, file);
                }
            }
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Object loaded;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = new Yaml(new SafeConstructor(options)).load(reader);
            }
            if (!(loaded instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("配置根节点必须是对象");
            }
            settings = parse(values);
        } catch (IOException | RuntimeException failure) {
            // YAML 异常可能携带凭据所在行，异常消息只报告文件路径
            throw new IllegalStateException("无法加载框架配置，请检查文件格式及字段类型: " + file);
        }
    }

    private static AtriSettings parse(Map<?, ?> values) {
        AtriSettings result = new AtriSettings();
        String mode = string(values, "qq.connection-mode", result.getQqConnectionMode()).trim().toLowerCase(Locale.ROOT);
        if (mode.equals("wh")) mode = "webhook";
        if (!mode.equals("ws") && !mode.equals("webhook")) mode = "ws";
        String webhookPath = string(values, "qq.webhook-path", result.getQqWebhookPath()).trim();
        if (!webhookPath.startsWith("/") || webhookPath.equals("/")) webhookPath = result.getQqWebhookPath();
        return result
                .setListenPort(integer(values, "listen-port", result.getListenPort(), 0, 65535))
                .setCommandPrefix(string(values, "command-prefix", result.getCommandPrefix()))
                .setDebugMode(bool(values, "debug-mode", result.isDebugMode()))
                .setOfficialBotEnabled(bool(values, "qq.enabled", result.isOfficialBotEnabled()))
                .setQqAppId(string(values, "qq.app-id", result.getQqAppId()))
                .setQqClientSecret(string(values, "qq.client-secret", result.getQqClientSecret()))
                .setQqApiBaseUrl(string(values, "qq.api-base-url", result.getQqApiBaseUrl()))
                .setQqConnectionMode(mode)
                .setQqWebhookPath(webhookPath)
                .setDebugGroupOpenId(string(values, "qq.debug-group-openId", result.getDebugGroupOpenId()))
                .setNapcatEnabled(bool(values, "napcat.enabled", result.isNapcatEnabled()))
                .setNapcatServerUrl(string(values, "napcat.server-url", result.getNapcatServerUrl()))
                .setAuthToken(string(values, "napcat.token", result.getAuthToken()))
                .setNapcatBotUin(string(values, "napcat.bot-uin", result.getNapcatBotUin()))
                .setNapcatDebugGroupUin(string(values, "napcat.debug-group-uin", result.getNapcatDebugGroupUin()))
                .setNapcatAdminUins(strings(values, "napcat.admin-uins"))
                .setNapcatMessageSpyGroups(strings(values, "napcat.message-spy-groups"))
                .setThreadMaxConcurrency(integer(values, "thread.max-concurrency", 0, 0, Integer.MAX_VALUE))
                .setThreadQueueCapacity(integer(values, "thread.queue-capacity", 4096, 1, Integer.MAX_VALUE))
                .setThreadScheduleCapacity(integer(values, "thread.schedule-capacity", 4096, 1, Integer.MAX_VALUE))
                .setQqWebhookQueueCapacity(integer(values, "qq.webhook-queue-capacity", 1024, 1, Integer.MAX_VALUE))
                .setNapcatQueueCapacity(integer(values, "napcat.queue-capacity", 1024, 1, Integer.MAX_VALUE))
                .setNapcatQueueBytes(number(values, "napcat.queue-bytes", 16L * 1024 * 1024, 1, Long.MAX_VALUE))
                .setNapcatWorkers(integer(values, "napcat.workers", 8, 1, 64));
    }

    private static Object value(Map<?, ?> values, String path) {
        Object current = values;
        for (String key : path.split("\\.")) {
            if (current == null) return null;
            if (!(current instanceof Map<?, ?> map)) throw new IllegalArgumentException(path);
            current = map.get(key);
        }
        return current;
    }

    private static String string(Map<?, ?> values, String path, String fallback) {
        Object value = value(values, path);
        if (value == null) return fallback;
        if (value instanceof String || value instanceof Number) return value.toString();
        throw new IllegalArgumentException(path);
    }

    private static boolean bool(Map<?, ?> values, String path, boolean fallback) {
        Object value = value(values, path);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("true")) return true;
            if (text.equalsIgnoreCase("false")) return false;
        }
        throw new IllegalArgumentException(path);
    }

    private static long number(Map<?, ?> values, String path, long fallback, long min, long max) {
        Object value = value(values, path);
        long parsed = value == null ? fallback : Long.parseLong(value.toString());
        if (parsed < min || parsed > max) throw new IllegalArgumentException(path);
        return parsed;
    }

    private static int integer(Map<?, ?> values, String path, int fallback, int min, int max) {
        return (int) number(values, path, fallback, min, max);
    }

    private static List<String> strings(Map<?, ?> values, String path) {
        Object value = value(values, path);
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path);
        return list.stream().map(item -> {
            if (item instanceof String || item instanceof Number) return item.toString();
            throw new IllegalArgumentException(path);
        }).toList();
    }
}
