package top.yzljc.atribot.webui;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.official.Ark23;

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

    /**
     * 校验并解析 WebUI 提交的 Ark 卡片内容
     *
     * @param body 包含 description、prompt 和 items 的卡片对象，条目可带 link
     * @return 可用于主动发送的 Ark23 卡片
     * @throws IllegalArgumentException 卡片结构或必填内容无效时抛出
     */
    public static Ark23 parseArk23(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("请填写 Ark 卡片内容");
        }
        if (!body.path("description").isTextual() || isBlank(body.path("description").asText())) {
            throw new IllegalArgumentException("Ark 卡片描述不能为空");
        }
        if (!body.path("prompt").isTextual() || isBlank(body.path("prompt").asText())) {
            throw new IllegalArgumentException("Ark 通知预览不能为空");
        }
        JsonNode items = body.path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new IllegalArgumentException("Ark 至少需要一条内容");
        }
        List<Ark23.Item> parsed = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isObject() || !item.path("description").isTextual() || isBlank(item.path("description").asText())) {
                throw new IllegalArgumentException("Ark 每条内容均不能为空");
            }
            JsonNode link = item.get("link");
            if (link != null && !link.isNull() && !link.isTextual()) {
                throw new IllegalArgumentException("Ark 条目链接必须为文本");
            }
            parsed.add(new Ark23.Item(item.path("description").asText().trim(),
                    link == null ? null : trimToNull(link.asText(null))));
        }
        return new Ark23(body.path("description").asText().trim(), body.path("prompt").asText().trim(), parsed);
    }

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
