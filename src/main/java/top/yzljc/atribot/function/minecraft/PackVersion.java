package top.yzljc.atribot.function.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * @Author YZ_Ljc_
 * @ClassName PackVersion
 * @Created_at 2026/07/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.minecraft
 */
@Slf4j
public record PackVersion(String name, int dataPackVersion, int resourcePackVersion) {

    private static volatile Map<String, PackVersion> versions = Map.of();
    private static final AtomicBoolean refreshing = new AtomicBoolean();

    public static void initAsync() {
        initAsync(ThreadManager.getExecutor(), () -> HttpService.sendGetRequest(ResourcesProperties.PACK_VERSION_API));
    }

    static void initAsync(Executor executor, Supplier<JsonNode> fetch) {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> loadVersions(fetch));
        } catch (RejectedExecutionException e) {
            refreshing.set(false);
            log.warn("Minecraft 资源包版本预加载未提交，保留现有缓存", e);
        }
    }

    public static void init() {
        if (!refreshing.compareAndSet(false, true)) return;
        loadVersions(() -> HttpService.sendGetRequest(ResourcesProperties.PACK_VERSION_API));
    }

    private static void loadVersions(Supplier<JsonNode> fetch) {
        try {
            log.info("开始加载 Minecraft 资源包版本");
            var data = fetch.get();
            if (data == null || !data.isArray() || data.isEmpty()) {
                log.warn("Minecraft 资源包版本加载失败或响应无效，保留现有缓存");
                return;
            }
            Map<String, PackVersion> loaded = new HashMap<>();
            for (var node : data) {
                String id = node.path("id").asText("");
                String name = node.path("name").asText("");
                if (id.isBlank() || name.isBlank()) continue;
                loaded.put(id, new PackVersion(name, node.path("data_pack_version").asInt(-1),
                        node.path("resource_pack_version").asInt(-1)));
            }
            if (loaded.isEmpty()) {
                log.warn("Minecraft 资源包版本响应不包含有效记录，保留现有缓存");
                return;
            }
            // 完整解析后一次替换，查询线程不会读到更新到一半的缓存。
            versions = Map.copyOf(loaded);
            log.info("Minecraft 资源包版本加载完成，共 {} 条", loaded.size());
        } catch (Exception e) {
            log.warn("Minecraft 资源包版本加载失败，保留现有缓存", e);
        } finally {
            refreshing.set(false);
        }
    }

    public static String getVersionNameByDataPackVersion(int dataPackVersion) {
        for (var version : versions.values()) {
            if (version.dataPackVersion() == dataPackVersion) {
                return version.name();
            }
        }
        init();
        for (var version : versions.values()) {
            if (version.dataPackVersion() == dataPackVersion) {
                return version.name();
            }
        }
        return null;
    }

    public static String getVersionNameByResourcePackVersion(int resourcePackVersion, boolean fillerTestVersion) {
        for (var version : versions.values()) {
            if (version.resourcePackVersion() == resourcePackVersion) {
                if (fillerTestVersion) {
                    if (version.name().contains("Snapshot") || version.name().contains("Release")) {
                        continue;
                    }
                }
                return version.name();
            }
        }
        init();
        for (var version : versions.values()) {
            if (version.resourcePackVersion() == resourcePackVersion) {
                if (fillerTestVersion) {
                    if (version.name().contains("Snapshot") || version.name().contains("Release")) {
                        continue;
                    }
                }
                return version.name();
            }
        }
        return null;
    }

    public static PackVersion getPackVersion(String versionId) {
        var d = versions.get(versionId);
        if (d != null) return d;
        init();
        return versions.get(versionId);
    }
}
