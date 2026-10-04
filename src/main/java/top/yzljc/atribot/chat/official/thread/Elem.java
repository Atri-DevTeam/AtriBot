package top.yzljc.atribot.chat.official.thread;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName Elem
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 */
public final class Elem {
    private final Map<String, Object> data;

    private Elem(int type, String key, Map<String, ?> content) {
        data = Map.of("type", type, key, content);
    }

    private Elem(Map<String, Object> data) {
        this.data = data;
    }

    /**
     * 解析元素并保留完整字段，未识别的类型可重新序列化。
     *
     * @param node 元素对象
     * @return 解析后的元素
     * @throws IllegalArgumentException 元素类型无效，或已知类型缺少对应内容对象
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Elem fromJson(JsonNode node) {
        Map<String, Object> data = RichText.objectFields(node, "Elem");
        JsonNode type = node.path("type");
        if (!type.isIntegralNumber() || !type.canConvertToInt()) {
            throw new IllegalArgumentException("Elem.type 必须为整数");
        }
        String key = switch (type.intValue()) {
            case 1 -> "text";
            case 2 -> "image";
            case 3 -> "video";
            case 4 -> "url";
            case 6 -> "at";
            default -> null;
        };
        if (key != null && !node.path(key).isObject()) {
            throw new IllegalArgumentException("Elem." + key + " 必须为对象");
        }
        return new Elem(data);
    }

    public int getType() {
        return ((Number) data.get("type")).intValue();
    }

    /**
     * 创建文本元素，多个样式可同时使用。
     *
     * @param text 正文，允许空字符串
     * @param styles 文本样式，省略时使用普通文本
     * @return 文本元素
     * @throws NullPointerException 正文、样式数组或其中的样式为 {@code null}
     */
    public static Elem text(String text, TextStyle... styles) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(styles, "styles");
        EnumSet<TextStyle> selected = EnumSet.noneOf(TextStyle.class);
        for (TextStyle style : styles) selected.add(Objects.requireNonNull(style, "style"));
        Map<String, Boolean> props = Map.of(
                "font_bold", selected.contains(TextStyle.BOLD),
                "italic", selected.contains(TextStyle.ITALIC),
                "underline", selected.contains(TextStyle.UNDERLINE));
        return new Elem(1, "text", Map.of("text", text, "props", props));
    }

    public static Elem image(String url) {
        return new Elem(2, "image", Map.of("third_url", requireUrl(url)));
    }

    /**
     * 创建带显示宽度比例的图片元素。
     *
     * @param url 第三方图片链接
     * @param widthPercent 原样传入平台 {@code width_percent} 字段的宽度比例
     * @return 图片元素
     * @throws IllegalArgumentException 链接为空白，或宽度比例不是有限正数
     * @throws NullPointerException 链接为 {@code null}
     */
    public static Elem image(String url, double widthPercent) {
        if (!Double.isFinite(widthPercent) || widthPercent <= 0) {
            throw new IllegalArgumentException("图片宽度比例必须为有限正数");
        }
        return new Elem(2, "image", Map.of("third_url", requireUrl(url), "width_percent", widthPercent));
    }

    public static Elem video(String url) {
        return new Elem(3, "video", Map.of("third_url", requireUrl(url)));
    }

    public static Elem url(String url, String description) {
        return new Elem(4, "url", Map.of("url", requireUrl(url),
                "desc", Objects.requireNonNull(description, "description")));
    }

    /**
     * 创建提及指定用户的元素，用户 ID 按字符串保存。
     *
     * @param userId 频道用户 ID
     * @param userName 用户显示名称
     * @return {@code type=6}、{@code at_type=1} 的用户提及元素
     * @throws IllegalArgumentException 用户 ID 为空白
     * @throws NullPointerException 用户 ID 或名称为 {@code null}
     */
    public static Elem atUser(String userId, String userName) {
        Objects.requireNonNull(userId, "userId");
        if (userId.isBlank()) throw new IllegalArgumentException("用户 ID 不能为空白");
        return new Elem(6, "at", Map.of("at_type", 1, "user_id", userId,
                "user_name", Objects.requireNonNull(userName, "userName")));
    }

    @JsonValue
    public Map<String, Object> toObject() {
        return RichText.copyObject(data);
    }

    private static String requireUrl(String url) {
        Objects.requireNonNull(url, "url");
        if (url.isBlank()) throw new IllegalArgumentException("资源链接不能为空白");
        return url;
    }
}
