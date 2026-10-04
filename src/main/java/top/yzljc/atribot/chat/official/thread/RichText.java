package top.yzljc.atribot.chat.official.thread;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName RichText
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 */
public final class RichText {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() {};
    private final List<Paragraph> paragraphs = new ArrayList<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * 解析富文本 JSON 字符串。
     *
     * @param json 标题或正文中的富文本 JSON 字符串
     * @return 解析后的富文本
     * @throws IllegalArgumentException JSON 格式或富文本结构无效
     * @throws NullPointerException 输入为 {@code null}
     */
    public static RichText fromJson(String json) {
        Objects.requireNonNull(json, "json");
        try {
            return fromJson(JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("富文本 JSON 格式无效", e);
        }
    }

    /**
     * 解析富文本对象或包含富文本 JSON 的字符串节点，保留未识别字段和元素。
     *
     * @param node 富文本对象节点，或标题、正文的字符串节点
     * @return 解析后的富文本
     * @throws IllegalArgumentException 输入节点或富文本结构无效
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RichText fromJson(JsonNode node) {
        if (node != null && node.isTextual()) {
            try {
                node = JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(node.textValue());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("富文本 JSON 格式无效", e);
            }
        }
        Map<String, Object> attributes = objectFields(node, "RichText");
        if (!node.path("paragraphs").isArray()) {
            throw new IllegalArgumentException("RichText.paragraphs 必须为数组");
        }
        RichText result = new RichText();
        attributes.remove("paragraphs");
        result.attributes.putAll(attributes);
        for (JsonNode paragraph : node.path("paragraphs")) result.paragraph(Paragraph.fromJson(paragraph));
        return result;
    }

    public List<Paragraph> getParagraphs() {
        return List.copyOf(paragraphs);
    }

    /**
     * 追加段落，序列化前对该段落的修改会反映在正文中。
     *
     * @param paragraph 段落，没有元素时表示空行
     * @return 当前富文本
     * @throws NullPointerException 段落为 {@code null}
     */
    public RichText paragraph(Paragraph paragraph) {
        paragraphs.add(Objects.requireNonNull(paragraph, "paragraph"));
        return this;
    }

    public RichText blankLine() {
        return paragraph(new Paragraph());
    }

    @JsonValue
    public Map<String, Object> toObject() {
        Map<String, Object> result = copyObject(attributes);
        result.put("paragraphs", paragraphs.stream().map(Paragraph::toObject).toList());
        return result;
    }

    /**
     * 序列化为帖子 {@code content} 所需的 JSON 字符串。
     *
     * @return 当前富文本的 JSON 表示
     * @throws IllegalStateException 序列化失败
     */
    public String toJson() {
        try {
            return JSON.writeValueAsString(toObject());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("论坛富文本序列化失败", e);
        }
    }

    @Override
    public String toString() {
        return toJson();
    }

    static Map<String, Object> objectFields(JsonNode node, String name) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(name + " 必须为对象");
        return copyObject(node);
    }

    static Map<String, Object> copyObject(Object value) {
        return JSON.convertValue(value, OBJECT_TYPE);
    }
}
