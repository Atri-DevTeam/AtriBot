package top.yzljc.atribot.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @Author YZ_Ljc_
 * @ClassName RS
 * @Created_at 2026/10/12
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
public final class RS {
    private static final Map<String, String> REQUEST = loadRequestProperties();

    private RS() {}

    /**
     * 从首次加载时缓存的 request.json 中读取配置，不展开地址中的占位符。
     *
     * @param key request.json 中的完整配置键名
     * @return 去除首尾空白的配置值
     * @throws IllegalStateException 配置项不存在或为空
     */
    public static String get(String key) {
        String value = REQUEST.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("request.json 缺少有效配置项: " + key);
        }
        return value.trim();
    }

    private static Map<String, String> loadRequestProperties() {
        Path path = Path.of(Properties.REQUEST);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("缺少请求资源配置文件: " + path.toAbsolutePath());
        }
        try {
            Map<String, String> values = new ObjectMapper().readValue(path.toFile(), new TypeReference<>() {});
            return values == null ? Map.of() : Map.copyOf(values);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取请求资源配置: " + path.toAbsolutePath(), e);
        }
    }
}
