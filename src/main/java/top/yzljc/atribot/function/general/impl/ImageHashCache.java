package top.yzljc.atribot.function.general.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName ImageHashCache
 * @Created_at 2026/07/12
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general.impl
 */
@Slf4j
public class ImageHashCache {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path DEFAULT_PATH = Path.of(Properties.HASH_DATA);
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private static volatile ImageHashCache instance;

    private final Map<String, String> endpointHashMap;

    private ImageHashCache(Map<String, String> map) {
        this.endpointHashMap = new ConcurrentHashMap<>(map);
    }

    public static ImageHashCache load() {
        return load(DEFAULT_PATH);
    }

    public static ImageHashCache load(Path path) {
        if (!Files.exists(path)) {
            log.warn("校验数据不存在: {}，哈希校验将自动跳过", path.toAbsolutePath());
            instance = new ImageHashCache(Collections.emptyMap());
            return instance;
        }

        try {
            JsonNode root = JSON.readTree(path.toFile());
            Map<String, String> map = new ConcurrentHashMap<>();

            root.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                // 跳过 _files 等元数据字段
                if (!key.startsWith("_") && value.isTextual()) {
                    map.put(key, value.asText());
                }
            });

            instance = new ImageHashCache(map);
            log.info("已加载校验数据: {} 个端点映射", map.size());
            return instance;
        } catch (IOException e) {
            log.error("加载校验数据失败: {}", path.toAbsolutePath(), e);
            instance = new ImageHashCache(Collections.emptyMap());
            return instance;
        }
    }

    public static ImageHashCache getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public String getExpectedHash(String endpoint) {
        return endpointHashMap.get(endpoint);
    }

    public Map<String, String> getMap() {
        return Collections.unmodifiableMap(endpointHashMap);
    }

    public boolean isEmpty() {
        return endpointHashMap.isEmpty();
    }

    /**
     * 校验 API 返回的哈希是否与预期一致。
     *
     * @param endpoint   端点名，如 "calendar"、"help"
     * @param actualHash 接口返回的 data.hash 字段值
     * @return 校验通过返回 true；无记录或哈希为 null 时返回 true（放行）；不匹配返回 false
     */
    public boolean validate(String endpoint, String actualHash) {
        if (isEmpty()) {
            log.warn("校验数据未加载，校验失败");
            return false;
        }
        if (actualHash == null) {
            log.warn("端点 {} 返回的哈希为 null，校验失败", endpoint);
            return false;
        }
        String expected = endpointHashMap.get(endpoint);
        if (expected == null) {
            log.info("端点 {} 无预置哈希记录，无需校验", endpoint);
            return true;
        }
        boolean ok = expected.equals(actualHash);
        if (!ok) {
            log.warn("哈希校验失败 [{}]: 预期={}, 实际={}", endpoint, expected, actualHash);
        }
        return ok;
    }
}
