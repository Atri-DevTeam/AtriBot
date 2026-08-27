package top.yzljc.atribot.function.tasks.pushtask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName PushTaskGlobalSettings
 * @Created_at 2026/08/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.pushtask
 */
@Slf4j
public final class PushTaskGlobalSettings {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, DisableRule> rules = new LinkedHashMap<>();
    private static boolean loaded = false;

    public static synchronized List<DisableRule> listRules() {
        ensureLoaded();
        return new ArrayList<>(rules.values());
    }

    public static synchronized DisableRule getRule(String functionId) {
        ensureLoaded();
        return rules.get(functionId);
    }

    public static synchronized DisableRule saveRule(DisableRule rule) {
        ensureLoaded();
        if (isBlank(rule.functionId())) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        DisableScope scope = rule.scope() == null ? DisableScope.BOTH : rule.scope();
        DisableRule normalized = new DisableRule(
                rule.functionId().trim(),
                blankToNull(rule.functionName()),
                scope,
                normalizeTime(rule.startTime()),
                normalizeTime(rule.endTime()),
                rule.enabled()
        );
        rules.put(normalized.functionId(), normalized);
        save();
        return normalized;
    }

    public static synchronized boolean removeRule(String functionId) {
        ensureLoaded();
        if (isBlank(functionId)) {
            return false;
        }
        boolean removed = rules.remove(functionId) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static boolean isDisabledForGroup(String functionId) {
        return isDisabled(functionId, DisableScope.GROUP);
    }

    public static boolean isDisabledForC2C(String functionId) {
        return isDisabled(functionId, DisableScope.C2C);
    }

    private static synchronized boolean isDisabled(String functionId, DisableScope targetScope) {
        ensureLoaded();
        DisableRule rule = rules.get(functionId);
        if (rule == null || !rule.enabled() || !rule.scope().matches(targetScope)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(BEIJING_ZONE);
        LocalDateTime start = parseTime(rule.startTime());
        LocalDateTime end = parseTime(rule.endTime());
        return (start == null || !now.isBefore(start)) && (end == null || !now.isAfter(end));
    }

    public static boolean isActiveNow(DisableRule rule) {
        if (rule == null || !rule.enabled()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(BEIJING_ZONE);
        LocalDateTime start = parseTime(rule.startTime());
        LocalDateTime end = parseTime(rule.endTime());
        return (start == null || !now.isBefore(start)) && (end == null || !now.isAfter(end));
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = new File(Properties.FUNCTION_SETTINGS);
        if (!file.exists()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(file);
            JsonNode list = root.path("rules");
            if (list.isArray()) {
                for (JsonNode node : list) {
                    DisableRule rule = parseRule(node);
                    if (rule != null) {
                        rules.put(rule.functionId(), rule);
                    }
                }
            }
            log.info("已加载推送任务全局禁用配置，共 {} 条", rules.size());
        } catch (IOException e) {
            log.error("加载推送任务全局禁用配置失败: {}", e.getMessage(), e);
        }
    }

    private static DisableRule parseRule(JsonNode node) {
        String functionId = text(node, "function_id", "functionId");
        if (isBlank(functionId)) {
            return null;
        }
        return new DisableRule(
                functionId.trim(),
                blankToNull(text(node, "function_name", "functionName")),
                parseScope(text(node, "scope")),
                normalizeTime(text(node, "start_time", "startTime")),
                normalizeTime(text(node, "end_time", "endTime")),
                !node.has("enabled") || node.path("enabled").asBoolean(true)
        );
    }

    private static void save() {
        try {
            File file = new File(Properties.FUNCTION_SETTINGS);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            List<Map<String, Object>> list = new ArrayList<>();
            for (DisableRule rule : rules.values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("function_id", rule.functionId());
                item.put("function_name", rule.functionName());
                item.put("scope", rule.scope().name());
                item.put("start_time", rule.startTime());
                item.put("end_time", rule.endTime());
                item.put("enabled", rule.enabled());
                list.add(item);
            }
            data.put("rules", list);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (IOException e) {
            log.error("保存推送任务全局禁用配置失败: {}", e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value.asText(null);
            }
        }
        return null;
    }

    private static DisableScope parseScope(String value) {
        if (isBlank(value)) {
            return DisableScope.BOTH;
        }
        try {
            return DisableScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DisableScope.BOTH;
        }
    }

    private static String normalizeTime(String value) {
        LocalDateTime time = parseTime(value);
        return time == null ? null : time.format(LOCAL_FORMATTER);
    }

    private static LocalDateTime parseTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().replace(' ', 'T');
        try {
            if (hasExplicitZone(normalized)) {
                return OffsetDateTime.parse(normalized).atZoneSameInstant(BEIJING_ZONE).toLocalDateTime();
            }
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static boolean hasExplicitZone(String value) {
        return value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$");
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum DisableScope {
        GROUP,
        C2C,
        BOTH;

        boolean matches(DisableScope target) {
            return this == BOTH || this == target;
        }
    }

    public record DisableRule(String functionId, String functionName, DisableScope scope,
                              String startTime, String endTime, boolean enabled) {
    }
}
