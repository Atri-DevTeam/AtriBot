package top.yzljc.atribot.i18n;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语言文本工具
 *
 * @Author YZ_Ljc_
 * @ClassName I18N
 * @Created_at 2026/09/29
 * @Project AtriMeow
 * @Package top.yzljc.atribot.i18n
 */
public final class I18N {
    public static final String FALLBACK_LANGUAGE = "zh_cn";

    private static final Logger log = LoggerFactory.getLogger(I18N.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(?:_[a-z0-9]{2,8}){0,2}");
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*");
    private static final Pattern PARAMETER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");
    private static final Map<String, Map<String, String>> BUNDLES = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED_MISSING_KEYS = ConcurrentHashMap.newKeySet();
    private static final Set<String> REPORTED_MISSING_PARAMETERS = ConcurrentHashMap.newKeySet();

    @Getter
    private static volatile String defaultLanguage = FALLBACK_LANGUAGE;

    private I18N() {
    }

    /**
     * 设置后续默认查询使用的语言，不修改 JVM 的 Locale 或项目配置文件。
     *
     * @param language 语言标识，例如 zh_cn、en_us；兼容 zh-CN 形式
     * @return 文件可用时返回 true；缺失、为空或格式错误时保留原默认语言并返回 false
     * @throws IllegalArgumentException 语言标识格式无效
     */
    public static boolean setDefaultLanguage(String language) {
        String normalized = normalizeLanguage(language);
        if (bundle(normalized).isEmpty()) return false;
        defaultLanguage = normalized;
        return true;
    }

    public static String text(String key) {
        return text(key, Map.of());
    }

    public static String text(String key, Map<String, ?> parameters) {
        return textFor(defaultLanguage, key, parameters);
    }

    public static String textFor(String language, String key) {
        return textFor(language, key, Map.of());
    }

    /**
     * 查询指定语言的文案，不改变全局默认语言。
     * 缺少翻译时回退中文；中文也缺失时返回 [key] 并记录一次警告。
     *
     * @param language 语言标识
     * @param key 语义键，例如 common.retry_later
     * @param parameters 命名参数；缺少或值为 null 的参数保留原占位符
     * @return 替换参数后的文案；参数值只替换一次，不作为模板再次解析
     * @throws IllegalArgumentException 语言标识或语义键格式无效
     */
    public static String textFor(String language, String key, Map<String, ?> parameters) {
        String normalized = normalizeLanguage(language);
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("语言文案键格式无效");
        }
        Objects.requireNonNull(parameters, "parameters");
        String template = bundle(normalized).get(key);
        String templateLanguage = normalized;
        if (template == null && !FALLBACK_LANGUAGE.equals(normalized)) {
            reportMissingKey(normalized, key);
            template = bundle(FALLBACK_LANGUAGE).get(key);
            templateLanguage = FALLBACK_LANGUAGE;
        }
        if (template == null) {
            reportMissingKey(templateLanguage, key);
            return "[" + key + "]";
        }
        return format(templateLanguage, key, template, parameters);
    }

    private static String normalizeLanguage(String language) {
        if (language == null) throw new IllegalArgumentException("语言标识不能为空");
        String normalized = language.strip().replace('-', '_').toLowerCase(Locale.ROOT);
        if (!LANGUAGE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("语言标识格式无效");
        }
        return normalized;
    }

    private static Map<String, String> bundle(String language) {
        return BUNDLES.computeIfAbsent(language, I18N::loadBundle);
    }

    /** 整份文件通过校验后才发布为只读缓存，避免使用部分解析结果。 */
    private static Map<String, String> loadBundle(String language) {
        String resource = "/lang/" + language + ".json";
        try (InputStream input = I18N.class.getResourceAsStream(resource)) {
            if (input == null) {
                log.warn("语言文件不存在: {}", resource);
                return Map.of();
            }
            JsonNode root = JSON.readTree(input);
            if (root == null || !root.isObject() || root.isEmpty()) {
                throw new IOException("语言文件必须是非空 JSON 对象");
            }
            Map<String, String> entries = new LinkedHashMap<>();
            var fields = root.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!KEY.matcher(field.getKey()).matches() || !field.getValue().isTextual()) {
                    throw new IOException("语言文件必须使用点分隔语义键和字符串值");
                }
                entries.put(field.getKey(), field.getValue().textValue());
            }
            return Map.copyOf(entries);
        } catch (IOException failure) {
            log.warn("语言文件加载失败: {}", resource, failure);
            return Map.of();
        }
    }

    private static void reportMissingKey(String language, String key) {
        if (REPORTED_MISSING_KEYS.add(language + ":" + key)) {
            log.warn("语言文案缺失: language={}, key={}", language, key);
        }
    }

    private static String format(String language, String key, String template, Map<String, ?> parameters) {
        Matcher matcher = PARAMETER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = parameters.get(name);
            if (value == null) {
                if (REPORTED_MISSING_PARAMETERS.add(language + ":" + key + ":" + name)) {
                    log.warn("语言文案参数缺失: language={}, key={}, parameter={}", language, key, name);
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
