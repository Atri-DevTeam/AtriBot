package top.yzljc.atribot.webui;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.official.ark.Ark;
import top.yzljc.atribot.chat.official.ark.Ark23;
import top.yzljc.atribot.chat.official.card.Card;

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

    /** 按模板解析 Ark，未指定模板时兼容原有的模板 23 请求。 */
    public static Ark parseArk(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("请填写 Ark 内容");
        }
        JsonNode template = body.get("templateId");
        if (template != null && (!template.isIntegralNumber() || !template.canConvertToInt())) {
            throw new IllegalArgumentException("Ark 模板编号无效");
        }
        int templateId = template == null ? 23 : template.intValue();
        return switch (templateId) {
            case 23 -> parseArk23(body);
            case 24 -> Ark.ark24(
                    requiredText(body, "description", "Ark 描述"),
                    requiredText(body, "prompt", "Ark 通知预览"),
                    requiredText(body, "title", "Ark 标题"),
                    optionalText(body, "metaDescription", "Ark 详情描述"),
                    requiredText(body, "picUrl", "Ark 图片链接"),
                    requiredText(body, "jumpUrl", "Ark 跳转链接"),
                    optionalText(body, "subTitle", "Ark 来源")
            );
            case 37 -> Ark.ark37(
                    requiredText(body, "prompt", "Ark 通知预览"),
                    requiredText(body, "title", "Ark 标题"),
                    optionalText(body, "subTitle", "Ark 子标题"),
                    requiredText(body, "picUrl", "Ark 图片链接"),
                    requiredText(body, "jumpUrl", "Ark 跳转链接")
            );
            default -> throw new IllegalArgumentException("不支持的 Ark 模板");
        };
    }

    /** 解析图文卡片。 */
    public static Card parseCard(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("请填写卡片内容");
        }
        String type = optionalText(body, "type", "卡片类型");
        if (!type.isEmpty() && !"tuwen".equals(type)) {
            throw new IllegalArgumentException("不支持的卡片类型");
        }
        return Card.tuWen(
                requiredText(body, "title", "卡片标题"),
                requiredText(body, "description", "卡片描述"),
                requiredText(body, "picUrl", "卡片图片链接"),
                requiredText(body, "jumpUrl", "卡片跳转链接")
        );
    }

    private static String requiredText(JsonNode body, String field, String label) {
        String value = optionalText(body, field, label);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value;
    }

    private static String optionalText(JsonNode body, String field, String label) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return "";
        if (!value.isTextual()) {
            throw new IllegalArgumentException(label + "必须为文本");
        }
        return value.asText().trim();
    }

    /**
     * 校验并解析 WebUI 提交的 Ark 卡片内容
     *
     * @param body 包含 description、prompt 和 items 的卡片对象，条目可带 link
     * @return 可用于主动发送的 Ark23 卡片
     * @throws IllegalArgumentException 卡片结构或必填内容无效时抛出
     */
    public static Ark parseArk23(JsonNode body) {
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
        return Ark.ark23(body.path("description").asText().trim(), body.path("prompt").asText().trim(), parsed);
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
