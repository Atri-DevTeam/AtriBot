package top.yzljc.atribot.chat.official.thread;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName Paragraph
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 */
public final class Paragraph {
    private final List<Elem> elems = new ArrayList<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public Paragraph() {
        attributes.put("props", Map.of("alignment", Alignment.LEFT.getValue()));
    }

    /**
     * 解析段落，保留空属性及平台扩展字段。
     *
     * @param node 段落对象
     * @return 解析后的段落
     * @throws IllegalArgumentException 段落、元素列表或属性结构无效
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Paragraph fromJson(JsonNode node) {
        Map<String, Object> attributes = RichText.objectFields(node, "Paragraph");
        if (!node.path("elems").isArray()) throw new IllegalArgumentException("Paragraph.elems 必须为数组");
        if (node.hasNonNull("props") && !node.path("props").isObject()) {
            throw new IllegalArgumentException("Paragraph.props 必须为对象");
        }
        Paragraph result = new Paragraph();
        attributes.remove("elems");
        result.attributes.clear();
        result.attributes.putAll(attributes);
        for (JsonNode elem : node.path("elems")) result.add(Elem.fromJson(elem));
        return result;
    }

    public List<Elem> getElems() {
        return List.copyOf(elems);
    }

    public Paragraph add(Elem elem) {
        elems.add(Objects.requireNonNull(elem, "elem"));
        return this;
    }

    public Paragraph alignment(Alignment alignment) {
        Objects.requireNonNull(alignment, "alignment");
        Map<String, Object> props = attributes.get("props") instanceof Map<?, ?> value
                ? RichText.copyObject(value) : new LinkedHashMap<>();
        props.put("alignment", alignment.getValue());
        attributes.put("props", props);
        return this;
    }

    public Paragraph text(String text, TextStyle... styles) {
        return add(Elem.text(text, styles));
    }

    public Paragraph image(String url) {
        return add(Elem.image(url));
    }

    public Paragraph image(String url, double widthPercent) {
        return add(Elem.image(url, widthPercent));
    }

    public Paragraph video(String url) {
        return add(Elem.video(url));
    }

    public Paragraph url(String url, String description) {
        return add(Elem.url(url, description));
    }

    public Paragraph atUser(String userId, String userName) {
        return add(Elem.atUser(userId, userName));
    }

    @JsonValue
    public Map<String, Object> toObject() {
        Map<String, Object> result = RichText.copyObject(attributes);
        result.put("elems", elems.stream().map(Elem::toObject).toList());
        return result;
    }
}
