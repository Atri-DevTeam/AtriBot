package top.yzljc.atribot.service.request;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;

import top.yzljc.atribot.configuration.Config;

/**
 * @Author YZ_Ljc_
 * @ClassName OpenApi
 * @Created_at 2026/10/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.request
 */
public final class OpenApi {

    private OpenApi() {}

    private static final class Holder {
        private static final Catalog CATALOG = new Catalog(
                () -> Config.getInstance().getUgcApiUrl(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), Clock.systemUTC());
    }

    /**
     * 首次访问或缓存过期时加载目录，刷新失败时继续使用同一服务地址的已有目录
     *
     * @param key 服务端公布的业务键名
     * @return 完整接口地址，保留路径参数占位符
     * @throws IllegalStateException 目录不可用或键名不存在
     */
    public static String get(String key) {
        return Holder.CATALOG.get(key);
    }

    public static String get(String key, String parameter, String value) {
        return get(key).replace("{" + parameter + "}", URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    public static String method(String key) {
        return Holder.CATALOG.method(key);
    }

    public static List<String> availableServices() {
        return Holder.CATALOG.availableServices();
    }

    /**
     * 立即更新目录，失败时保留已有缓存
     *
     * @throws IllegalStateException 目录获取或校验失败
     */
    public static void refresh() {
        Holder.CATALOG.refresh();
    }

    static final class Catalog {
        private static final long TTL_MILLIS = Duration.ofMinutes(5).toMillis();
        private static final long RETRY_MILLIS = Duration.ofSeconds(30).toMillis();
        private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

        private final Supplier<String> baseUrl;
        private final HttpClient client;
        private final Clock clock;
        private Snapshot snapshot;
        private long retryAt;
        private String attemptedBase;

        Catalog(Supplier<String> baseUrl, HttpClient client, Clock clock) {
            this.baseUrl = baseUrl;
            this.client = client;
            this.clock = clock;
        }

        String get(String key) {
            return entry(key).url();
        }

        String method(String key) {
            return entry(key).method();
        }

        List<String> availableServices() {
            return current().services();
        }

        Entry entry(String key) {
            Entry entry = current().entries().get(key);
            if (entry == null) {
                throw new IllegalStateException("图片接口未提供: " + key);
            }
            return entry;
        }

        synchronized Snapshot current() {
            String base = normalizeBase(baseUrl.get());
            long now = clock.millis();
            boolean usable = snapshot != null && snapshot.base().equals(base);
            if (usable && now < snapshot.expiresAt()) {
                return snapshot;
            }
            if (base.equals(attemptedBase) && now < retryAt) {
                if (usable) return snapshot;
                throw new IllegalStateException("图片接口目录暂不可用");
            }
            try {
                return fetch(base);
            } catch (IllegalStateException e) {
                if (usable) return snapshot;
                throw e;
            }
        }

        synchronized void refresh() {
            fetch(normalizeBase(baseUrl.get()));
        }

        private Snapshot fetch(String base) {
            attemptedBase = base;
            retryAt = clock.millis() + RETRY_MILLIS;
            try {
                BizResponse<JsonNode> response = Requests.get(client, base + "/v3/openapi", Duration.ofSeconds(5));
                if (!response.isSuccess()) throw new IllegalArgumentException(response.message());
                JsonNode data = response.data();
                if (data == null || !data.isObject()) throw new IllegalArgumentException("目录格式异常");
                if (!data.path("api").isArray() || !data.path("available_service").isArray()) {
                    throw new IllegalArgumentException("目录格式异常");
                }
                Map<String, Entry> entries = new LinkedHashMap<>();
                for (JsonNode api : data.path("api")) {
                    String key = api.path("key").asText("");
                    String path = api.path("path").asText("");
                    String method = api.path("method").asText("");
                    if (key.isBlank() || !METHODS.contains(method) || !validPath(path)
                            || entries.putIfAbsent(key, new Entry(base + path, method)) != null) {
                        throw new IllegalArgumentException("目录接口定义异常: " + key);
                    }
                }
                List<String> services = new ArrayList<>();
                for (JsonNode service : data.path("available_service")) {
                    if (!service.isTextual() || service.asText().isBlank()) {
                        throw new IllegalArgumentException("图片发布方式格式异常");
                    }
                    services.add(service.asText());
                }
                snapshot = new Snapshot(base, Map.copyOf(entries), List.copyOf(services), clock.millis() + TTL_MILLIS);
                retryAt = 0;
                return snapshot;
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalStateException("图片接口目录暂不可用", e);
            }
        }

        private static boolean validPath(String path) {
            if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")) return false;
            try {
                URI uri = URI.create(path.replaceAll("\\{[A-Za-z][A-Za-z0-9_]*}", "parameter"));
                return uri.getRawQuery() == null && uri.getRawFragment() == null
                        && uri.getRawAuthority() == null && uri.normalize().equals(uri)
                        && !uri.getPath().contains("..") && !uri.getPath().contains("\\");
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        private static String normalizeBase(String value) {
            if (value == null || value.isBlank()) throw new IllegalStateException("未配置图片服务地址");
            String base = value.trim().replaceAll("/+$", "");
            URI uri;
            try {
                uri = URI.create(base);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("图片服务地址格式无效", e);
            }
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalStateException("图片服务地址格式无效");
            }
            return base;
        }
    }

    private record Entry(String url, String method) {}

    private record Snapshot(String base, Map<String, Entry> entries, List<String> services, long expiresAt) {}
}
