package top.yzljc.atribot.webui;

import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName WebUiSupport
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.impl
 */
public final class WebUiSupport {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    public static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /** 从请求体数组字段取非空字符串列表 */
    public static List<String> stringList(JsonNode body, String field) {
        List<String> list = new ArrayList<>();
        if (body != null && body.path(field).isArray()) {
            for (JsonNode node : body.path(field)) {
                String value = node.asText(null);
                if (value != null && !value.isBlank()) {
                    list.add(value);
                }
            }
        }
        return list;
    }

    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public static String nullToDash(String value) {
        return value == null ? "-" : value;
    }

    public static String formatFeedbackTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime().format(TIME_FMT) : null;
    }
}
